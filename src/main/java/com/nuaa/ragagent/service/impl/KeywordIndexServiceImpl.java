package com.nuaa.ragagent.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.exception.BusinessException;
import com.nuaa.ragagent.mapper.KbChunkMapper;
import com.nuaa.ragagent.response.SearchChunkResponse;
import com.nuaa.ragagent.service.KeywordIndexService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * @author jiyunhe
 */
@Service
public class KeywordIndexServiceImpl implements KeywordIndexService {

    private static final String INDEX_NAME = "kb_chunk_content";

    private static final int CHUNK_STATUS_NORMAL = 1;

    private static final int CHUNK_EMBEDDING_STATUS_SUCCESS = 1;

    private final ElasticsearchClient esClient;

    private final KbChunkMapper kbChunkMapper;

    public KeywordIndexServiceImpl(ElasticsearchClient esClient, KbChunkMapper kbChunkMapper) {
        this.esClient = esClient;
        this.kbChunkMapper = kbChunkMapper;
    }

    /**
     * ES 为强依赖：启动时即建索引，ES 不可用则应用启动失败。
     */
    @PostConstruct
    public void init() {
        ensureIndex();
    }

    @Override
    public void ensureIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (exists) {
                return;
            }

            esClient.indices().create(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("chunkId", p -> p.long_(l -> l.index(true)))
                            .properties("documentId", p -> p.long_(l -> l.index(true)))
                            .properties("spaceId", p -> p.long_(l -> l.index(true)))
                            .properties("chunkIndex", p -> p.integer(i -> i.index(true)))
                            .properties("content", p -> p.text(t -> t
                                    .analyzer("smartcn")
                                    .searchAnalyzer("smartcn")))
                    ));
        } catch (IOException e) {
            throw new BusinessException("初始化 Elasticsearch 索引失败: " + e.getMessage());
        }
    }

    @Override
    public void upsertChunk(KbChunk chunk) {
        try {
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(chunk.getId()))
                    .document(toDoc(chunk)));
        } catch (IOException e) {
            throw new BusinessException("写入 Elasticsearch 失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        try {
            esClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t
                            .field("documentId")
                            .value(FieldValue.of(documentId))))
                    .refresh(true));
        } catch (IOException e) {
            throw new BusinessException("删除 Elasticsearch 索引失败: " + e.getMessage());
        }
    }

    @Override
    public List<SearchChunkResponse> search(String query, Long spaceId, int limit) {
        try {
            SearchResponse<KeywordIndexDoc> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .query(q -> q.bool(b -> b
                                    .must(m -> m.match(mt -> mt.field("content").query(query)))
                                    .filter(f -> f.term(t -> t
                                            .field("spaceId")
                                            .value(FieldValue.of(spaceId))))))
                            .size(limit),
                    KeywordIndexDoc.class);

            List<SearchChunkResponse> results = new ArrayList<>();
            for (Hit<KeywordIndexDoc> hit : response.hits().hits()) {
                KeywordIndexDoc doc = hit.source();
                if (doc == null) {
                    continue;
                }

                double keywordScore = hit.score() == null ? 0.0 : hit.score();

                SearchChunkResponse item = new SearchChunkResponse();
                item.setChunkId(doc.chunkId());
                item.setDocumentId(doc.documentId());
                item.setSpaceId(doc.spaceId());
                item.setChunkIndex(doc.chunkIndex());
                item.setContent(doc.content());
                item.setKeywordScore(keywordScore);
                item.setFinalScore(keywordScore);
                item.setScore(keywordScore);
                item.setRetrievalSource("KEYWORD");

                results.add(item);
            }

            return results;
        } catch (IOException e) {
            throw new BusinessException("Elasticsearch 检索失败: " + e.getMessage());
        }
    }

    @Override
    public void rebuildAll() {
        List<KbChunk> chunks = kbChunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getStatus, CHUNK_STATUS_NORMAL)
                        .eq(KbChunk::getEmbeddingStatus, CHUNK_EMBEDDING_STATUS_SUCCESS)
        );

        for (KbChunk chunk : chunks) {
            upsertChunk(chunk);
        }
    }

    private KeywordIndexDoc toDoc(KbChunk chunk) {
        return new KeywordIndexDoc(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getSpaceId(),
                chunk.getChunkIndex(),
                chunk.getContent()
        );
    }

    /**
     * ES 文档载荷：仅保存 content 及 4 个 id 字段。
     */
    private record KeywordIndexDoc(long chunkId, long documentId, long spaceId, int chunkIndex, String content) {
    }
}
