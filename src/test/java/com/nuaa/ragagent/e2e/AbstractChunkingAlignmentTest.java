package com.nuaa.ragagent.e2e;

import com.nuaa.ragagent.common.ApiResponse;
import com.nuaa.ragagent.entity.KbChunk;
import com.nuaa.ragagent.entity.KbDocument;
import com.nuaa.ragagent.entity.KbSpace;
import com.nuaa.ragagent.request.CreateDocumentRequest;
import com.nuaa.ragagent.request.CreateSpaceRequest;
import com.nuaa.ragagent.util.Chunker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 切分管线对齐验证：确认「系统真实上传链路写入 kb_chunk 的分块」与
 * 「离线对同一语料做切分得到的分块」逐字符一致。
 *
 * <p>动机：评测集的 expectedChunkIds 是在离线切分产物上人工标注的。如果离线切分
 * 与系统实际入库的分块有任何偏差，标注就会指向错误的 chunk，评测结果全部失真。
 * 本测试把这条假设变成可执行的断言，防止后续切分参数或实现改动悄悄破坏两者的一致性。</p>
 *
 * <p>做法：创建独立空间 → 通过真实 HTTP 接口逐篇上传 MyBatis 语料 → 读取库中 chunk →
 * 与注入的 {@link Chunker}（即系统正在使用的那个策略实现）离线切分结果逐字符比对，
 * 同时校验 chunkIndex 连续、charCount 一致。</p>
 *
 * <p>使用 fake embedding，因此只影响向量质量，不影响分块边界；测试结束物理清理数据。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestAiConfiguration.class)
abstract class AbstractChunkingAlignmentTest {

    private static final Path MARKDOWN_DIR =
            Paths.get("dataset", "mybatis-3.5.19-zh", "processed", "markdown");

    /** 与 ChunkingStrategyDumpTest 保持一致：剥离抓取元数据，只留正文 */
    private static final Pattern FRONT_MATTER =
            Pattern.compile("^---\\n.*?\\n---\\n", Pattern.DOTALL);

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 系统当前实际使用的切分策略实现（由 rag.chunk.strategy 决定） */
    @Autowired
    protected Chunker systemChunker;

    private String baseUrl;
    private Long spaceId;

    @BeforeAll
    void initBaseUrl() {
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    void cleanup() {
        if (spaceId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM kb_index_task WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM kb_chunk WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM kb_document WHERE space_id = ?", spaceId);
        jdbcTemplate.update("DELETE FROM kb_space WHERE id = ?", spaceId);
    }

    @Test
    @Order(1)
    void uploadedChunksMatchOfflineChunking() throws IOException {
        ApiResponse<KbSpace> spaceResp = post("/api/spaces",
                new CreateSpaceRequest()
                        .setName("align-" + System.currentTimeMillis())
                        .setDescription("chunking alignment check"),
                new ParameterizedTypeReference<ApiResponse<KbSpace>>() {
                });
        assertThat(spaceResp.getCode()).isZero();
        spaceId = spaceResp.getData().getId();

        List<Path> mdFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MARKDOWN_DIR, "*.md")) {
            for (Path md : stream) {
                mdFiles.add(md);
            }
        }
        mdFiles.sort(Path::compareTo);
        assertThat(mdFiles).isNotEmpty();

        int comparedChunks = 0;
        for (Path md : mdFiles) {
            String key = md.getFileName().toString().replace(".md", "");
            String content = stripFrontMatter(Files.readString(md, StandardCharsets.UTF_8));

            Long documentId = createDocument(key, content);
            List<KbChunk> stored = listChunks(documentId);
            List<String> expected = systemChunker.split(content);

            assertThat(stored)
                    .as("文档 %s 的 chunk 数量（系统写入 vs 离线切分）", key)
                    .hasSize(expected.size());

            for (int i = 0; i < expected.size(); i++) {
                KbChunk chunk = stored.get(i);
                assertThat(chunk.getChunkIndex())
                        .as("文档 %s 第 %d 个 chunk 的 chunkIndex", key, i)
                        .isEqualTo(i);
                assertThat(chunk.getContent())
                        .as("文档 %s 第 %d 个 chunk 的内容", key, i)
                        .isEqualTo(expected.get(i));
                assertThat(chunk.getCharCount())
                        .as("文档 %s 第 %d 个 chunk 的 charCount", key, i)
                        .isEqualTo(expected.get(i).length());
            }

            comparedChunks += expected.size();
        }

        assertThat(comparedChunks).isGreaterThan(0);
        System.out.println("切分对齐验证通过：策略=" + systemChunker.getClass().getSimpleName()
                + "，文档数=" + mdFiles.size() + "，比对 chunk 数=" + comparedChunks);
    }

    private String stripFrontMatter(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        return FRONT_MATTER.matcher(normalized).replaceFirst("").stripLeading();
    }

    private Long createDocument(String title, String content) {
        ApiResponse<KbDocument> resp = post("/api/documents",
                new CreateDocumentRequest()
                        .setSpaceId(spaceId)
                        .setTitle(title)
                        .setContent(content)
                        .setSourceType("official_website")
                        .setSourceUri("https://mybatis.org/mybatis-3/zh_CN/" + title + ".html"),
                new ParameterizedTypeReference<ApiResponse<KbDocument>>() {
                });
        assertThat(resp.getCode()).isZero();
        return resp.getData().getId();
    }

    private List<KbChunk> listChunks(Long documentId) {
        ApiResponse<List<KbChunk>> resp = get("/api/documents/" + documentId + "/chunks",
                new ParameterizedTypeReference<ApiResponse<List<KbChunk>>>() {
                });
        return resp.getData();
    }

    private <T> ApiResponse<T> get(String path, ParameterizedTypeReference<ApiResponse<T>> typeRef) {
        ResponseEntity<ApiResponse<T>> entity =
                restTemplate.exchange(baseUrl + path, HttpMethod.GET, null, typeRef);
        ApiResponse<T> response = entity.getBody();
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isZero();
        return response;
    }

    private <T> ApiResponse<T> post(String path, Object body,
                                    ParameterizedTypeReference<ApiResponse<T>> typeRef) {
        ResponseEntity<ApiResponse<T>> entity =
                restTemplate.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body), typeRef);
        ApiResponse<T> response = entity.getBody();
        assertThat(response).isNotNull();
        return response;
    }
}
