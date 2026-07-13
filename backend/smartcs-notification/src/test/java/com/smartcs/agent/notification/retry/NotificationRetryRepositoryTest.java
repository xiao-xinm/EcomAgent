package com.smartcs.agent.notification.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationRetryRepositoryTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void claimDueLocksAndLeasesAcceptedAndFailedEventFields() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    Instant occurredAt = Instant.parse("2026-07-13T06:00:00Z");
                    when(rs.getString("event_id")).thenReturn("ntf_test");
                    when(rs.getString("trace_id")).thenReturn("trace_test");
                    when(rs.getString("event_type")).thenReturn("APPROVAL_APPROVED");
                    when(rs.getString("recipient_user_id")).thenReturn("u1001");
                    when(rs.getString("session_id")).thenReturn("s_test");
                    when(rs.getString("ticket_id")).thenReturn("wo_test");
                    when(rs.getString("operator_id")).thenReturn("agent001");
                    when(rs.getString("channel")).thenReturn("USER_SESSION");
                    when(rs.getString("title")).thenReturn("审批通过通知");
                    when(rs.getString("content")).thenReturn("审批已通过");
                    when(rs.getString("payload")).thenReturn("{\"decision\":\"APPROVED\"}");
                    when(rs.getInt("retry_count")).thenReturn(1);
                    when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(occurredAt));
                    return List.of(mapper.mapRow(rs, 0));
                });
        NotificationRetryRepository repository =
                new NotificationRetryRepository(jdbcTemplate, new ObjectMapper());

        List<NotificationRetryEvent> events = repository.claimDue("worker-a", 20, 5, 120000);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventId()).isEqualTo("ntf_test");
        assertThat(events.get(0).payload()).containsEntry("decision", "APPROVED");
        assertThat(events.get(0).toCommand().attempt()).isEqualTo(2);
        verify(jdbcTemplate).query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class), any(Object[].class));
        verify(jdbcTemplate).update(contains("delivery_lease_until = TIMESTAMPADD"), any(Object[].class));
    }

    @Test
    void statusUpdatesRemainRestrictedToOpenDeliveryStates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        NotificationRetryRepository repository =
                new NotificationRetryRepository(jdbcTemplate, new ObjectMapper());

        repository.markDelivered("ntf_test", "worker-a");
        repository.markFailed(
                "ntf_test",
                "worker-a",
                "channel unavailable",
                Instant.parse("2026-07-13T06:01:00Z"));

        verify(jdbcTemplate).update(contains("status = 'DELIVERED'"), any(Object[].class));
        verify(jdbcTemplate).update(contains("retry_count = retry_count + 1"), any(Object[].class));
        verify(jdbcTemplate, times(2)).update(contains("delivery_owner = ?"), any(Object[].class));
    }
}
