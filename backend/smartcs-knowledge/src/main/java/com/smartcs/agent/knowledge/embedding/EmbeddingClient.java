package com.smartcs.agent.knowledge.embedding;

import java.util.List;
import java.util.Optional;

/**
 * 文本向量化边界。
 *
 * <p>Knowledge 检索层只依赖该接口，不直接依赖具体模型供应商。
 */
public interface EmbeddingClient {

    Optional<EmbeddingResult> embed(String text);

    record EmbeddingResult(String model, List<Double> vector, int promptTokens) {
    }
}
