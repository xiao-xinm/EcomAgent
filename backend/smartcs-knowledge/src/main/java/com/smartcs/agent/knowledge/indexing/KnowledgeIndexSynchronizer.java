package com.smartcs.agent.knowledge.indexing;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqItem;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexWriter.IndexOperationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** MySQL FAQ 到可重建检索索引的同步编排器。 */
@Service
public class KnowledgeIndexSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeIndexSynchronizer.class);

    private final List<KnowledgeIndexWriter> writers;

    @Autowired
    public KnowledgeIndexSynchronizer(ObjectProvider<KnowledgeIndexWriter> writerProvider) {
        this(writerProvider.orderedStream().toList());
    }

    KnowledgeIndexSynchronizer(List<KnowledgeIndexWriter> writers) {
        this.writers = List.copyOf(writers);
    }

    public IndexSyncSummary synchronize(FaqItem faq) {
        if (writers.isEmpty()) {
            return IndexSyncSummary.disabled();
        }
        FaqIndexDocument document = toDocument(faq);
        List<IndexOperationResult> operations = writers.stream()
                .map(writer -> safeUpsert(writer, document))
                .toList();
        IndexSyncSummary summary = summarize(1, operations);
        logSummary("incremental", summary);
        return summary;
    }

    /**
     * 非破坏性地重放 FAQ 文档，不清空 Elasticsearch 或 pgvector 中的现有索引。
     */
    public IndexSyncSummary repair(List<FaqItem> faqs) {
        if (writers.isEmpty()) {
            return IndexSyncSummary.disabled();
        }
        List<IndexOperationResult> operations = new ArrayList<>();
        for (KnowledgeIndexWriter writer : writers) {
            for (FaqItem faq : faqs) {
                operations.add(safeUpsert(writer, toDocument(faq)));
            }
        }
        IndexSyncSummary summary = summarize(faqs.size(), operations);
        logSummary("repair", summary);
        return summary;
    }

    public IndexSyncSummary rebuild(List<FaqItem> faqs) {
        if (writers.isEmpty()) {
            return IndexSyncSummary.disabled();
        }
        List<IndexOperationResult> operations = new ArrayList<>();
        for (KnowledgeIndexWriter writer : writers) {
            IndexOperationResult reset = safeReset(writer);
            operations.add(reset);
            if (!reset.success()) {
                continue;
            }
            for (FaqItem faq : faqs) {
                operations.add(safeUpsert(writer, toDocument(faq)));
            }
        }
        IndexSyncSummary summary = summarize(faqs.size(), operations);
        logSummary("rebuild", summary);
        return summary;
    }

    private IndexOperationResult safeUpsert(KnowledgeIndexWriter writer, FaqIndexDocument document) {
        try {
            return writer.upsert(document);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "知识索引写入器抛出未处理异常 writer={} faqId={} reason={}",
                    writer.name(),
                    document.faqId(),
                    exception.getMessage());
            return IndexOperationResult.failure(writer.name(), exception.getMessage());
        }
    }

    private IndexOperationResult safeReset(KnowledgeIndexWriter writer) {
        try {
            return writer.reset();
        } catch (RuntimeException exception) {
            LOGGER.warn("知识索引重置异常 writer={} reason={}", writer.name(), exception.getMessage());
            return IndexOperationResult.failure(writer.name(), exception.getMessage());
        }
    }

    private FaqIndexDocument toDocument(FaqItem faq) {
        List<String> keywords = new LinkedHashSet<>(faq.keywords()).stream()
                .map(String::trim)
                .filter(keyword -> !keyword.isBlank())
                .sorted(Comparator.naturalOrder())
                .toList();
        String canonical = String.join(
                "\u001f",
                faq.faqId(),
                faq.question(),
                faq.answer(),
                String.join("\u001e", keywords),
                faq.category(),
                faq.status(),
                String.valueOf(faq.priority()),
                String.valueOf(faq.updatedAt()));
        return new FaqIndexDocument(
                faq.faqId(),
                faq.question(),
                faq.answer(),
                keywords,
                faq.category(),
                faq.status(),
                faq.priority(),
                sha256(canonical),
                faq.updatedAt());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    private IndexSyncSummary summarize(int documentCount, List<IndexOperationResult> operations) {
        int succeeded = (int) operations.stream().filter(IndexOperationResult::success).count();
        return new IndexSyncSummary(
                true,
                documentCount,
                succeeded,
                operations.size() - succeeded,
                List.copyOf(operations));
    }

    private void logSummary(String action, IndexSyncSummary summary) {
        LOGGER.info(
                "知识索引同步完成 action={} documentCount={} successCount={} failureCount={}",
                action,
                summary.documentCount(),
                summary.successCount(),
                summary.failureCount());
    }

    public record IndexSyncSummary(
            boolean enabled,
            int documentCount,
            int successCount,
            int failureCount,
            List<IndexOperationResult> operations
    ) {

        public static IndexSyncSummary disabled() {
            return new IndexSyncSummary(false, 0, 0, 0, List.of());
        }

        public boolean allSucceeded() {
            return enabled && failureCount == 0;
        }
    }
}
