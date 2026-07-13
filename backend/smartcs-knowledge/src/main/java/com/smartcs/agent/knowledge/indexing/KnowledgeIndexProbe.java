package com.smartcs.agent.knowledge.indexing;

/** 可重建知识索引的只读状态探针。 */
public interface KnowledgeIndexProbe {

    String name();

    ProbeResult probe();

    record ProbeResult(
            String name,
            boolean available,
            Long documentCount,
            String message
    ) {

        public static ProbeResult ready(String name, long documentCount) {
            return new ProbeResult(name, true, documentCount, "ready");
        }

        public static ProbeResult unavailable(String name, String message) {
            return new ProbeResult(name, false, null, message);
        }
    }
}
