package com.smartcs.agent.workbench.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationOutboxRepositoryTest {

    @Test
    void enqueuePersistsStableEventIdAndSerializedRequest() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificationOutboxRepository repository = repository(jdbcTemplate);
        NotificationEventRequest request = request();

        repository.enqueue(request);

        verify(jdbcTemplate).update(
                contains("INSERT INTO workbench_notification_outbox"),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findDueMapsStoredRequestAndAttemptCount() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificationOutboxRepository repository = repository(jdbcTemplate);
        ObjectMapper mapper = mapper();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("event_id")).thenReturn("ntf_test");
        when(resultSet.getString("event_payload")).thenReturn(mapper.writeValueAsString(request()));
        when(resultSet.getInt("attempt_count")).thenReturn(2);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<NotificationOutboxEvent> rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });

        List<NotificationOutboxEvent> events = repository.findDue(20, 5);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventId()).isEqualTo("ntf_test");
        assertThat(events.get(0).attemptCount()).isEqualTo(2);
        assertThat(events.get(0).request().ticketId()).isEqualTo("wo_test");
        assertThat(events.get(0).request().payload()).containsEntry("decision", "APPROVED");
    }

    @Test
    void statusUpdatesOnlyTouchOpenOutboxStates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificationOutboxRepository repository = repository(jdbcTemplate);

        repository.markSent("ntf_test");
        repository.markFailed("ntf_test", "temporary failure", null);

        verify(jdbcTemplate).update(contains("status = 'SENT'"), any(Object[].class));
        verify(jdbcTemplate).update(contains("status = 'FAILED'"), any(Object[].class));
    }

    private NotificationOutboxRepository repository(JdbcTemplate jdbcTemplate) {
        return new NotificationOutboxRepository(jdbcTemplate, mapper());
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private NotificationEventRequest request() {
        return new NotificationEventRequest(
                "ntf_test",
                "trace_test",
                "smartcs-workbench",
                "APPROVAL_APPROVED",
                "u1001",
                "s_test",
                "wo_test",
                "agent001",
                "USER_SESSION",
                "审批通过通知",
                "审批已通过",
                Map.of("decision", "APPROVED"),
                Instant.parse("2026-07-13T07:00:00Z"));
    }
}
