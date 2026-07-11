package com.smartcs.agent.knowledge.retrieval;

import java.util.List;

/** Elasticsearch 关键词检索边界。 */
public interface KeywordRetrievalClient {

    List<KnowledgeRetrievalCandidate> search(String question, int topK);
}
