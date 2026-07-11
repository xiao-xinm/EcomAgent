package com.smartcs.agent.knowledge.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqItem;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexWriter.IndexOperationResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeIndexSynchronizerTest {

    @Test
    void synchronizeWritesSameCanonicalDocumentToAllWriters() {
        KnowledgeIndexWriter elasticsearch = writer("elasticsearch");
        KnowledgeIndexWriter pgvector = writer("pgvector");
        KnowledgeIndexSynchronizer synchronizer =
                new KnowledgeIndexSynchronizer(List.of(elasticsearch, pgvector));

        KnowledgeIndexSynchronizer.IndexSyncSummary summary = synchronizer.synchronize(faq("ACTIVE"));

        assertThat(summary.allSucceeded()).isTrue();
        assertThat(summary.successCount()).isEqualTo(2);
        verify(elasticsearch).upsert(any(FaqIndexDocument.class));
        verify(pgvector).upsert(any(FaqIndexDocument.class));
    }

    @Test
    void rebuildSkipsWriterDocumentsWhenItsResetFails() {
        KnowledgeIndexWriter elasticsearch = writer("elasticsearch");
        KnowledgeIndexWriter pgvector = writer("pgvector");
        when(pgvector.reset()).thenReturn(IndexOperationResult.failure("pgvector", "unavailable"));
        KnowledgeIndexSynchronizer synchronizer =
                new KnowledgeIndexSynchronizer(List.of(elasticsearch, pgvector));

        KnowledgeIndexSynchronizer.IndexSyncSummary summary = synchronizer.rebuild(List.of(faq("ACTIVE")));

        assertThat(summary.failureCount()).isEqualTo(1);
        verify(elasticsearch).upsert(any(FaqIndexDocument.class));
        verify(pgvector, never()).upsert(any(FaqIndexDocument.class));
    }

    @Test
    void synchronizeIsDisabledWhenHybridWritersAreAbsent() {
        KnowledgeIndexSynchronizer synchronizer = new KnowledgeIndexSynchronizer(List.of());

        KnowledgeIndexSynchronizer.IndexSyncSummary summary = synchronizer.synchronize(faq("ACTIVE"));

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.operations()).isEmpty();
    }

    private KnowledgeIndexWriter writer(String name) {
        KnowledgeIndexWriter writer = mock(KnowledgeIndexWriter.class);
        when(writer.name()).thenReturn(name);
        when(writer.reset()).thenReturn(IndexOperationResult.success(name, "reset"));
        when(writer.upsert(any(FaqIndexDocument.class)))
                .thenReturn(IndexOperationResult.success(name, "upserted"));
        return writer;
    }

    private FaqItem faq(String status) {
        Instant now = Instant.parse("2026-07-11T07:00:00Z");
        return new FaqItem(
                "faq_refund_arrival",
                "退款多久到账",
                "退款通常会在审核后原路退回。",
                List.of("到账", "退款", "退款"),
                "after_sale",
                status,
                100,
                now,
                now);
    }
}
