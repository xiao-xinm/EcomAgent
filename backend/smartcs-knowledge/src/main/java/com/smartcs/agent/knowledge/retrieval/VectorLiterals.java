package com.smartcs.agent.knowledge.retrieval;

import java.util.List;
import java.util.stream.Collectors;

/** pgvector 文本向量字面量转换。 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String from(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("Embedding 向量不能为空");
        }
        return vector.stream()
                .peek(value -> {
                    if (value == null || !Double.isFinite(value)) {
                        throw new IllegalArgumentException("Embedding 包含非法数值");
                    }
                })
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
