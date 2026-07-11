package com.smartcs.agent.knowledge.retrieval;

import java.util.List;

/** pgvector 语义检索边界。 */
public interface VectorRetrievalClient {

    List<KnowledgeRetrievalCandidate> search(String question, int topK);
}
