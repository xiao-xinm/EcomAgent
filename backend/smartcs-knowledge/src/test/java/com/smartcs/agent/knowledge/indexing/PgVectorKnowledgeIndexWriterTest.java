package com.smartcs.agent.knowledge.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient.EmbeddingResult;
import com.smartcs.agent.knowledge.retrieval.PgVectorKnowledgeStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PgVectorKnowledgeIndexWriterTest {

    @Test
    void upsertEmbedsActiveDocumentAndWritesVector() {
        PgVectorKnowledgeStore store = mock(PgVectorKnowledgeStore.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        FaqIndexDocument document = document("ACTIVE");
        when(embeddingClient.embed("退款多久到账\n退款通常会在审核后原路退回。\n退款 到账"))
                .thenReturn(Optional.of(new EmbeddingResult(
                        "text-embedding-v4",
                        List.of(0.1D, -0.2D, 0.3D),
                        12)));
        PgVectorKnowledgeIndexWriter writer = new PgVectorKnowledgeIndexWriter(store, embeddingClient, 3);

        KnowledgeIndexWriter.IndexOperationResult result = writer.upsert(document);

        assertThat(result.success()).isTrue();
        verify(store).upsert(document, "text-embedding-v4", 3, "[0.1,-0.2,0.3]");
    }

    @Test
    void upsertDeletesInactiveDocumentWithoutCallingEmbedding() {
        PgVectorKnowledgeStore store = mock(PgVectorKnowledgeStore.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        PgVectorKnowledgeIndexWriter writer = new PgVectorKnowledgeIndexWriter(store, embeddingClient, 3);

        KnowledgeIndexWriter.IndexOperationResult result = writer.upsert(document("DISABLED"));

        assertThat(result.success()).isTrue();
        verify(store).delete("faq_refund_arrival");
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void upsertReturnsFailureWhenEmbeddingIsUnavailable() {
        PgVectorKnowledgeStore store = mock(PgVectorKnowledgeStore.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        PgVectorKnowledgeIndexWriter writer = new PgVectorKnowledgeIndexWriter(store, embeddingClient, 3);

        KnowledgeIndexWriter.IndexOperationResult result = writer.upsert(document("ACTIVE"));

        assertThat(result.success()).isFalse();
        verifyNoInteractions(store);
    }

    private FaqIndexDocument document(String status) {
        return new FaqIndexDocument(
                "faq_refund_arrival",
                "退款多久到账",
                "退款通常会在审核后原路退回。",
                List.of("退款", "到账"),
                "after_sale",
                status,
                100,
                "abc123",
                Instant.parse("2026-07-11T07:00:00Z"));
    }
}
