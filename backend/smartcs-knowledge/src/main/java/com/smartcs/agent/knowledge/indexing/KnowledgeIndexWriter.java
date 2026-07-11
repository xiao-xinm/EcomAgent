package com.smartcs.agent.knowledge.indexing;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;

/** 单个可重建知识索引的写入边界。 */
public interface KnowledgeIndexWriter {

    String name();

    IndexOperationResult upsert(FaqIndexDocument document);

    IndexOperationResult reset();

    record IndexOperationResult(String writer, boolean success, String message) {

        public static IndexOperationResult success(String writer, String message) {
            return new IndexOperationResult(writer, true, message);
        }

        public static IndexOperationResult failure(String writer, String message) {
            return new IndexOperationResult(writer, false, message);
        }
    }
}
