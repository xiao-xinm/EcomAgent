package com.smartcs.agent.workbench.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthSource;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.auth.PrincipalType;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WorkbenchTicketEventStreamTest {

    @Test
    void mergesMultipleSourceChangesForTheSameTicketBeforeBroadcasting() {
        WorkbenchTicketChangeRepository repository = mock(WorkbenchTicketChangeRepository.class);
        Instant connectedAt = Instant.parse("2026-07-16T08:00:00Z");
        TicketChangedEvent workOrderEvent = new TicketChangedEvent(
                "work-order:wo_1:1",
                "wo_1",
                "PROCESSING",
                "agent_001",
                connectedAt.plusSeconds(1));
        TicketChangedEvent auditEvent = new TicketChangedEvent(
                "evt_note_1",
                "wo_1",
                "PROCESSING",
                "agent_001",
                connectedAt.plusSeconds(2));
        when(repository.currentCursor()).thenReturn(new TicketChangeCursor(connectedAt, ""));
        when(repository.findWorkOrderChanges(any(), eq(100))).thenAnswer(invocation -> {
            return new TicketChangeBatch(
                    List.of(workOrderEvent),
                    new TicketChangeCursor(workOrderEvent.changedAt(), workOrderEvent.ticketId()));
        });
        when(repository.findAuditChanges(any(), eq(100))).thenAnswer(invocation -> {
            return new TicketChangeBatch(
                    List.of(auditEvent),
                    new TicketChangeCursor(auditEvent.changedAt(), auditEvent.eventId()));
        });
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        WorkbenchTicketEventStream stream = new WorkbenchTicketEventStream(
                repository,
                100,
                15_000,
                1_800_000,
                Clock.fixed(connectedAt, ZoneOffset.UTC),
                timeout -> emitter);

        stream.open(agentPrincipal());
        stream.pollOnce();

        assertThat(emitter.sentEvents).hasSize(2);
        verify(repository).findWorkOrderChanges(any(), eq(100));
        verify(repository).findAuditChanges(any(), eq(100));
    }

    @Test
    void rejectsInvalidTimingConfiguration() {
        WorkbenchTicketChangeRepository repository = mock(WorkbenchTicketChangeRepository.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WorkbenchTicketEventStream(
                        repository,
                        100,
                        999,
                        1_800_000,
                        Clock.systemUTC(),
                        SseEmitter::new))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置无效");
    }

    private AuthenticatedPrincipal agentPrincipal() {
        return AuthenticatedPrincipal.operator(
                "agent_001",
                PrincipalType.AGENT,
                Set.of(AuthRoles.AGENT),
                AuthSource.DEV_HEADER);
    }

    private static class CapturingSseEmitter extends SseEmitter {

        private final List<SseEventBuilder> sentEvents = new ArrayList<>();

        private CapturingSseEmitter() {
            super(1_800_000L);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            sentEvents.add(builder);
        }
    }
}
