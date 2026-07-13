package com.smartcs.agent.knowledge.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.knowledge.indexing.KnowledgeIndexProbe.ProbeResult;
import com.smartcs.agent.knowledge.retrieval.PgVectorKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class PgVectorKnowledgeIndexProbeTest {

    @Test
    void probeCountsVectorsForCurrentModelAndDimensions() {
        PgVectorKnowledgeStore store = mock(PgVectorKnowledgeStore.class);
        when(store.countActive("text-embedding-v4", 1024)).thenReturn(7L);
        PgVectorKnowledgeIndexProbe probe =
                new PgVectorKnowledgeIndexProbe(store, "text-embedding-v4", 1024);

        ProbeResult result = probe.probe();

        assertThat(result.available()).isTrue();
        assertThat(result.documentCount()).isEqualTo(7L);
        verify(store).countActive("text-embedding-v4", 1024);
    }

    @Test
    void probeReturnsUnavailableWhenPostgresCannotBeReached() {
        PgVectorKnowledgeStore store = mock(PgVectorKnowledgeStore.class);
        when(store.countActive("text-embedding-v4", 1024))
                .thenThrow(new DataAccessResourceFailureException("postgres unavailable"));
        PgVectorKnowledgeIndexProbe probe =
                new PgVectorKnowledgeIndexProbe(store, "text-embedding-v4", 1024);

        ProbeResult result = probe.probe();

        assertThat(result.available()).isFalse();
        assertThat(result.documentCount()).isNull();
        assertThat(result.message()).isEqualTo("postgres unavailable");
    }
}
