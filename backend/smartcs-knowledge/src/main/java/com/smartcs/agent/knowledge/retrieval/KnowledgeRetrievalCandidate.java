package com.smartcs.agent.knowledge.retrieval;

/**
 * 单个检索引擎返回的知识候选。
 *
 * <p>score 只用于当前检索引擎内部排序；混合检索不得直接比较 ES 与 pgvector 的原始分数。
 */
public record KnowledgeRetrievalCandidate(
        String faqId,
        String question,
        String answer,
        String category,
        String source,
        double score
) {
}
