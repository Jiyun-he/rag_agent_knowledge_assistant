package com.nuaa.ragagent.e2e;

import org.springframework.test.context.TestPropertySource;

/**
 * STRUCTURE_AWARE 策略下的切分管线对齐验证：与 FIXED_SIZE 用例完全相同的比对逻辑，
 * 仅把切分策略切换为结构感知，确保两种策略下离线切分都与系统写入一致。
 *
 * <p>用 {@link TestPropertySource} 而非在子类重复写 {@code @SpringBootTest}——
 * 后者会整体覆盖父类注解，导致 {@code webEnvironment = RANDOM_PORT} 丢失。</p>
 */
@TestPropertySource(properties = "rag.chunk.strategy=STRUCTURE_AWARE")
class StructureAwareChunkingAlignmentTest extends AbstractChunkingAlignmentTest {
}
