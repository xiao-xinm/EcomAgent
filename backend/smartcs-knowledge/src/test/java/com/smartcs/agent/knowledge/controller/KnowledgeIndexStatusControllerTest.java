package com.smartcs.agent.knowledge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.knowledge.dto.KnowledgeIndexStatusDtos.KnowledgeIndexStatus;
import com.smartcs.agent.knowledge.dto.KnowledgeIndexStatusDtos.KnowledgeStoreStatus;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexStatusService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeIndexStatusControllerTest {

    @Test
    void statusReturnsCurrentIndexConsistency() {
        KnowledgeIndexStatusService service = mock(KnowledgeIndexStatusService.class);
        KnowledgeIndexStatus expected = new KnowledgeIndexStatus(
                "hybrid",
                true,
                true,
                true,
                new KnowledgeStoreStatus("mysql", true, 7L, "ready"),
                List.of(
                        new KnowledgeStoreStatus("elasticsearch", true, 7L, "ready"),
                        new KnowledgeStoreStatus("pgvector", true, 7L, "ready")),
                Instant.parse("2026-07-13T06:00:00Z"));
        when(service.inspect()).thenReturn(expected);
        KnowledgeIndexStatusController controller = new KnowledgeIndexStatusController(service);

        ApiResponse<KnowledgeIndexStatus> response = controller.status();

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(expected);
        assertThat(response.data().consistent()).isTrue();
        verify(service).inspect();
    }
}
