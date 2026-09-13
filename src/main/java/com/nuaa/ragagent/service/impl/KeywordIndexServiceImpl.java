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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * @author jiyunhe
 */
@Service
public class KeywordIndexServiceImpl implements KeywordIndexService {

    private static final String INDEX_NAME = "kb_chunk_content";

    private static final int CHUNK_STATUS_NORMAL = 1;

    private static final int CHUNK_EMBEDDING_STATUS_SUCCESS = 1;

    /** 对账时 ES scroll 每批大小 */
    private static final int SCROLL_SIZE = 500;

    /** 对账时 ES scroll 上下文存活时间 */
    private static final String SCROLL_KEEP_ALIVE = "30s";

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

    @Override
    public int reconcileOrphans(Set<Long> validChunkIds, Set<Long> existingChunkIds) {
        try {
            Set<Long> indexChunkIds = new HashSet<>();
            int scanned = 0;

            SearchResponse<KeywordIndexDoc> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(SCROLL_SIZE)
                            .scroll(t -> t.time(SCROLL_KEEP_ALIVE)),
                    KeywordIndexDoc.class);

            collectIndexHits(indexChunkIds, response.hits().hits());
            scanned = indexChunkIds.size();

            String scrollId = response.scrollId();
            if (scrollId != null) {
                while (true) {
                    String currentScrollId = scrollId;
                    co.elastic.clients.elasticsearch.core.ScrollResponse<KeywordIndexDoc> next =
                            esClient.scroll(s -> s.scrollId(currentScrollId).scroll(t -> t.time(SCROLL_KEEP_ALIVE)),
                                    KeywordIndexDoc.class);

                    if (next.hits().hits().isEmpty()) {
                        clearScroll(currentScrollId);
                        break;
                    }

                    collectIndexHits(indexChunkIds, next.hits().hits());
                    scanned = indexChunkIds.size();

                    String nextScrollId = next.scrollId();
                    if (nextScrollId == null || nextScrollId.equals(currentScrollId)) {
                        break;
                    }
                    scrollId = nextScrollId;
                }
            }

            existingChunkIds.addAll(indexChunkIds);

            List<Long> orphans = indexChunkIds.stream()
                    .filter(chunkId -> !validChunkIds.contains(chunkId))
                    .toList();

            if (orphans.isEmpty()) {
                return 0;
            }

            esClient.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.terms(t -> t
                                    .field("chunkId")
                                    .terms(tq -> tq.value(orphans.stream().map(FieldValue::of).toList()))))))
                    .refresh(true));

            return orphans.size();
        } catch (IOException e) {
            throw new BusinessException("Elasticsearch 对账失败: " + e.getMessage());
        }
    }

    private void collectIndexHits(Set<Long> target, List<Hit<KeywordIndexDoc>> hits) {
        for (Hit<KeywordIndexDoc> hit : hits) {
            KeywordIndexDoc doc = hit.source();
            if (doc != null) {
                target.add(doc.chunkId());
            }
        }
    }

    private void clearScroll(String scrollId) {
        try {
            esClient.clearScroll(c -> c.scrollId(scrollId));
        } catch (IOException e) {
            // 清理 scroll 上下文失败不影响对账结果
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
