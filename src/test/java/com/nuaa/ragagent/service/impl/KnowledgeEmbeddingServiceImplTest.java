package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.enums.DocumentStatus;
import com.nuaa.ragagent.exception.IndexBuildAbortedException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.response.IndexBuildResult;
import com.nuaa.ragagent.service.KeywordIndexService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证索引构建的"重试只处理失败、保留已成功部分"语义、force 模式全量重写，以及文档状态流转：
 * FAILED →（重试时置回 INDEXING）→ 全成功 ACTIVE / 仍有失败 FAILED。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeEmbeddingServiceImplTest {

    /** 与 application.yml 中 rag.retrieval.vector-max-top-k 的默认值保持一致 */
    private static final int MAX_TOP_K = 50;

    @Mock
    private KbSpaceMapper spaceMapper;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private KbChunkMapper chunkMapper;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private KeywordIndexService keywordIndexService;

    private KnowledgeEmbeddingServiceImpl service;

    @BeforeAll
    static void initMybatisLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, KbDocument.class);
        TableInfoHelper.initTableInfo(assistant, KbChunk.class);
        TableInfoHelper.initTableInfo(assistant, KbSpace.class);
    }

    @BeforeEach
    void setUp() {
        service = new KnowledgeEmbeddingServiceImpl(spaceMapper, documentMapper, chunkMapper,
                vectorStore, keywordIndexService, "rag_kb_chunks_test", MAX_TOP_K);
        // 默认视为文档有效：收尾前的锁定读返回 ACTIVE，未命中该 stub 的用例（如中止路径）单独覆盖
        lenient().when(documentMapper.selectStatusForShare(any()))
                .thenReturn(DocumentStatus.ACTIVE.getValue());
    }

    @Test
    void buildDocumentIndex_retriesOnlyFailedKeepsSuccessAndBecomesActive() {
        // 文档 FAILED 状态（=3）
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L).setStatus(3);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(documentMapper.update(any(), any())).thenReturn(1);
        when(chunkMapper.selectCount(any())).thenReturn(3L);
        when(chunkMapper.update(any(), any())).thenReturn(1);

        // 三个 chunk：成功(=1，应保留不重复处理)、失败(=2，应重试)、待处理(=0)
        KbChunk success = new KbChunk().setId(1L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(0).setContent("c1").setEmbeddingStatus(1).setStatus(1);
        KbChunk failed = new KbChunk().setId(2L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(1).setContent("c2").setEmbeddingStatus(2).setStatus(1);
        KbChunk pending = new KbChunk().setId(3L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(2).setContent("c3").setEmbeddingStatus(0).setStatus(1);
        // 待处理集合 = failed + pending（成功 chunk 不在其中，不被重复处理）
        when(chunkMapper.selectList(any())).thenReturn(List.of(failed, pending));

        IndexBuildResult result = service.buildDocumentIndex(1L, false);

        // 只处理 2 个（failed+pending），成功的 chunk 未重复写入索引
        verify(vectorStore, times(2)).add(anyList());
        verify(keywordIndexService, times(2)).upsertChunk(any());
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);

        // 文档状态：FAILED → 重试开始置回 INDEXING → 全成功置 ACTIVE(1)
        ArgumentCaptor<LambdaUpdateWrapper<KbDocument>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(documentMapper, times(2)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<KbDocument>> wrappers = captor.getAllValues();
        assertThat(wrappers).hasSize(2);
        assertThat(wrappers.get(1).getParamNameValuePairs().values()).contains(1); // 最终 ACTIVE
    }

    @Test
    void buildDocumentIndex_retryStillFails_keepsFailed() {
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L).setStatus(3);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(documentMapper.update(any(), any())).thenReturn(1);
        when(chunkMapper.selectCount(any())).thenReturn(1L);
        when(chunkMapper.update(any(), any())).thenReturn(1);

        KbChunk failed = new KbChunk().setId(2L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(0).setContent("c2").setEmbeddingStatus(2).setStatus(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(failed));

        // 构建仍然抛错
        org.mockito.Mockito.doThrow(new RuntimeException("embedding down"))
                .when(vectorStore).add(anyList());

        IndexBuildResult result = service.buildDocumentIndex(1L, false);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);

        // 最终状态 FAILED(3)
        ArgumentCaptor<LambdaUpdateWrapper<KbDocument>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(documentMapper, times(2)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<KbDocument>> wrappers = captor.getAllValues();
        assertThat(wrappers).hasSize(2);
        assertThat(wrappers.get(1).getParamNameValuePairs().values()).contains(3); // 保持 FAILED
    }

    @Test
    void buildDocumentIndex_force_rebuildsAllChunksIncludingAlreadySuccess() {
        // 文档 ACTIVE（=1），REPAIR_INDEX 强制重建场景
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L).setStatus(1);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(documentMapper.update(any(), any())).thenReturn(1);
        when(chunkMapper.selectCount(any())).thenReturn(2L);
        when(chunkMapper.update(any(), any())).thenReturn(1);

        // 两个 chunk 均已成功（embedding_status=1），force 模式仍全量重写
        KbChunk c1 = new KbChunk().setId(1L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(0).setContent("c1").setEmbeddingStatus(1).setStatus(1);
        KbChunk c2 = new KbChunk().setId(2L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(1).setContent("c2").setEmbeddingStatus(1).setStatus(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(c1, c2));

        IndexBuildResult result = service.buildDocumentIndex(1L, true);

        verify(vectorStore, times(2)).add(anyList());
        verify(keywordIndexService, times(2)).upsertChunk(any());
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isZero();

        // ACTIVE → 置回 INDEXING → 全成功置回 ACTIVE：共 2 次 update，最终 ACTIVE(1)
        ArgumentCaptor<LambdaUpdateWrapper<KbDocument>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(documentMapper, times(2)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<KbDocument>> wrappers = captor.getAllValues();
        assertThat(wrappers).hasSize(2);
        assertThat(wrappers.get(1).getParamNameValuePairs().values()).contains(1);
    }

    @Test
    void buildDocumentIndex_documentInvalidatedDuringBuild_abortsAndCleansUpInsteadOfResurrecting() {
        // 文档进入构建时为 INDEXING，但收尾前已被并发删除/更新置为 INVALID
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L)
                .setStatus(DocumentStatus.INDEXING.getValue());
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(chunkMapper.selectCount(any())).thenReturn(1L);
        when(chunkMapper.update(any(), any())).thenReturn(1);
        when(documentMapper.selectStatusForShare(1L)).thenReturn(DocumentStatus.INVALID.getValue());

        KbChunk pending = new KbChunk().setId(1L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(0).setContent("c1").setEmbeddingStatus(0).setStatus(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(pending));

        assertThatThrownBy(() -> service.buildDocumentIndex(1L, false))
                .isInstanceOf(IndexBuildAbortedException.class);

        // 本次已写入的 Qdrant / ES 索引必须被清理，否则会成为孤儿数据
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(keywordIndexService).deleteByDocumentId(1L);
        // 且绝不能把文档状态写回（否则就是“复活”已删除文档）
        verify(documentMapper, never()).update(isNull(), any());
    }

    @Test
    void buildDocumentIndex_activeDocumentManualRebuild_isNotTreatedAsAborted() {
        // 对 ACTIVE 文档手动触发 BUILD_INDEX：非 force 不重置为 INDEXING，构建成功后终态仍是 ACTIVE。
        // 此时 UPDATE 匹配但值未变化（影响 0 行），不得据此误判为“文档已失效”而删掉健康索引。
        KbDocument document = new KbDocument().setId(1L).setSpaceId(1L)
                .setStatus(DocumentStatus.ACTIVE.getValue());
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(documentMapper.selectStatusForShare(1L)).thenReturn(DocumentStatus.ACTIVE.getValue());
        when(chunkMapper.selectCount(any())).thenReturn(1L);
        when(chunkMapper.update(any(), any())).thenReturn(1);

        KbChunk pending = new KbChunk().setId(1L).setDocumentId(1L).setSpaceId(1L)
                .setChunkIndex(0).setContent("c1").setEmbeddingStatus(0).setStatus(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(pending));

        IndexBuildResult result = service.buildDocumentIndex(1L, false);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        // 只有收尾一次文档状态写入，且为 ACTIVE
        ArgumentCaptor<LambdaUpdateWrapper<KbDocument>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(documentMapper, times(1)).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(DocumentStatus.ACTIVE.getValue());
        // 未发生中止清理
        verify(keywordIndexService, never()).deleteByDocumentId(any());
    }
}
