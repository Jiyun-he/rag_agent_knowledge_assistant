package com.nuaa.ragagent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.enums.TaskType;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.mapper.KbDocumentMapper;
import com.nuaa.ragagent.mapper.KbSpaceMapper;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.UpdateDocumentRequest;
import com.nuaa.ragagent.service.IndexTaskService;
import com.nuaa.ragagent.util.Chunker;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证文档操作不再直接执行索引工作，而是事务提交后登记持久化索引任务：
 * 创建 → BUILD_INDEX(新文档)；删除 → DELETE_INDEX(文档)；
 * 更新 = DELETE_INDEX(旧文档) + BUILD_INDEX(新文档)。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private KbSpaceMapper spaceMapper;

    @Mock
    private KbChunkMapper chunkMapper;

    @Mock
    private Chunker chunker;

    @Mock
    private IndexTaskService indexTaskService;

    private KnowledgeDocumentServiceImpl service;

    @BeforeAll
    static void initMybatisLambdaCache() {
        // 纯单测环境无 MyBatis 运行时，手动初始化 lambda cache 供 LambdaWrapper 解析
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, KbDocument.class);
        TableInfoHelper.initTableInfo(assistant, KbChunk.class);
        TableInfoHelper.initTableInfo(assistant, KbSpace.class);
    }

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocumentServiceImpl(documentMapper, spaceMapper, chunkMapper,
                chunker, indexTaskService);
    }

    @Test
    void create_registersBuildIndexTaskAfterCommit() {
        KbSpace space = new KbSpace().setId(1L).setStatus(1);
        when(spaceMapper.selectOne(any())).thenReturn(space);
        when(chunker.split("content")).thenReturn(List.of("c1", "c2"));
        // insert 后回填 id；getById 在插入后查询返回该文档
        KbDocument[] insertedRef = new KbDocument[1];
        when(documentMapper.insert(any(KbDocument.class))).thenAnswer(inv -> {
            KbDocument doc = inv.getArgument(0);
            doc.setId(1L);
            insertedRef[0] = doc;
            return 1;
        });
        when(documentMapper.selectOne(any())).thenAnswer(inv -> insertedRef[0]);
        when(documentMapper.updateById(any(KbDocument.class))).thenReturn(1);

        CreateDocumentRequest request = new CreateDocumentRequest()
                .setSpaceId(1L).setTitle("t").setContent("content");

        try (var tsm = mockStatic(TransactionSynchronizationManager.class)) {
            KbDocument result = service.create(request);

            assertThat(result.getId()).isEqualTo(1L);
            // 新文档初始 INDEXING
            assertThat(result.getStatus()).isEqualTo(2);

            // chunk 重建（2 个，embedding_status=0）
            ArgumentCaptor<KbChunk> chunkCaptor = ArgumentCaptor.forClass(KbChunk.class);
            verify(chunkMapper, times(2)).insert(chunkCaptor.capture());
            assertThat(chunkCaptor.getAllValues()).extracting(KbChunk::getEmbeddingStatus).containsOnly(0);

            // 提交后登记 BUILD_INDEX(新文档)
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
            syncCaptor.getValue().afterCommit();
            verify(indexTaskService).createTask(1L, TaskType.BUILD_INDEX);
            verify(indexTaskService, never()).createTask(any(), eq(TaskType.DELETE_INDEX));
        }
    }

    @Test
    void delete_invalidatesDocumentDeletesChunksAndRegistersDeleteTaskAfterCommit() {
        KbDocument oldDocument = new KbDocument()
                .setId(1L).setSpaceId(1L).setStatus(1).setSourceType("MANUAL")
                .setTitle("old").setContent("old content");
        when(documentMapper.selectOne(any())).thenReturn(oldDocument);

        try (var tsm = mockStatic(TransactionSynchronizationManager.class)) {
            service.delete(1L);

            // 文档失效（软删 status=0）
            ArgumentCaptor<LambdaUpdateWrapper<KbDocument>> updateCaptor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(documentMapper).update(isNull(), updateCaptor.capture());
            assertThat(updateCaptor.getValue().getSqlSet()).contains("status=");
            assertThat(updateCaptor.getValue().getParamNameValuePairs().values()).contains(0);

            // chunk 物理删除
            ArgumentCaptor<LambdaQueryWrapper<KbChunk>> chunkWrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(chunkMapper).delete(chunkWrapperCaptor.capture());

            // 提交后只登记 DELETE_INDEX，不登记 BUILD
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
            syncCaptor.getValue().afterCommit();
            verify(indexTaskService).createTask(1L, TaskType.DELETE_INDEX);
            verify(indexTaskService, never()).createTask(eq(1L), eq(TaskType.BUILD_INDEX));
        }
    }

    @Test
    void update_invalidatesOldBuildsNewAndRegistersDeletePlusBuildTasksAfterCommit() {
        KbDocument oldDocument = new KbDocument()
                .setId(1L).setSpaceId(1L).setStatus(1).setSourceType("MANUAL")
                .setTitle("old").setContent("old content");
        // selectOne：新文档插入前返回旧文档，插入后返回新文档（update 内部 new 的对象）
        KbDocument[] insertedRef = new KbDocument[1];
        when(documentMapper.selectOne(any())).thenAnswer(inv ->
                insertedRef[0] != null ? insertedRef[0] : oldDocument);
        when(documentMapper.insert(any(KbDocument.class))).thenAnswer(inv -> {
            KbDocument doc = inv.getArgument(0);
            doc.setId(2L);
            insertedRef[0] = doc;
            return 1;
        });
        when(documentMapper.updateById(any(KbDocument.class))).thenReturn(1);
        when(chunker.split("new content")).thenReturn(List.of("chunk-a", "chunk-b"));

        UpdateDocumentRequest request = new UpdateDocumentRequest()
                .setTitle("new title")
                .setContent("new content");

        try (var tsm = mockStatic(TransactionSynchronizationManager.class)) {
            KbDocument result = service.update(1L, request);

            // 返回的是新建文档（新 id），而非旧文档
            assertThat(result.getId()).isEqualTo(2L);

            // 旧文档失效
            ArgumentCaptor<LambdaUpdateWrapper<KbDocument>> updateCaptor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(documentMapper).update(isNull(), updateCaptor.capture());
            assertThat(updateCaptor.getValue().getSqlSet()).contains("status=");
            assertThat(updateCaptor.getValue().getParamNameValuePairs().values()).contains(0);

            // 旧 chunk 物理删除
            verify(chunkMapper).delete(any());

            // 新文档初始 INDEXING
            ArgumentCaptor<KbDocument> insertedCaptor = ArgumentCaptor.forClass(KbDocument.class);
            verify(documentMapper, times(1)).insert(insertedCaptor.capture());
            assertThat(insertedCaptor.getValue().getStatus()).isEqualTo(2);

            // 提交后登记 DELETE_INDEX(旧) + BUILD_INDEX(新)
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
            syncCaptor.getValue().afterCommit();
            verify(indexTaskService).createTask(1L, TaskType.DELETE_INDEX);
            verify(indexTaskService).createTask(2L, TaskType.BUILD_INDEX);
        }
    }
}
