package com.smartcs.agent.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventView;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class NotificationEventServiceTest {

    @Test
    void acceptPersistsEventAndReturnsAcceptedResult() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        NotificationEventService service = new NotificationEventService(jdbcTemplate, new ObjectMapper());
        NotificationEventRequest request = new NotificationEventRequest(
                "evt_test",
                "trace_notice",
                "smartcs-workbench",
                "APPROVAL_APPROVED",
                "u1001",
                "s_test",
                "wo_test",
                "agent001",
                "",
                "审批通过通知",
                "审批已通过",
                Map.of("ticketId", "wo_test"),
                Instant.parse("2026-07-05T01:00:00Z"));

        NotificationEventResult result = service.accept(request);

        assertThat(result.eventId()).isEqualTo("evt_test");
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.channel()).isEqualTo("USER_SESSION");
        verify(jdbcTemplate).update(contains("INSERT INTO notification_event"), any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listReturnsMappedNotificationEvents() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM notification_event"),
                eq(Long.class),
                any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(
                contains("FROM notification_event"),
                any(RowMapper.class),
                any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    Instant now = Instant.parse("2026-07-05T01:00:00Z");
                    when(rs.getString("event_id")).thenReturn("evt_test");
                    when(rs.getString("trace_id")).thenReturn("trace_notice");
                    when(rs.getString("source_service")).thenReturn("smartcs-workbench");
                    when(rs.getString("event_type")).thenReturn("APPROVAL_APPROVED");
                    when(rs.getString("recipient_user_id")).thenReturn("u1001");
                    when(rs.getString("session_id")).thenReturn("s_test");
                    when(rs.getString("ticket_id")).thenReturn("wo_test");
                    when(rs.getString("operator_id")).thenReturn("agent001");
                    when(rs.getString("channel")).thenReturn("USER_SESSION");
                    when(rs.getString("title")).thenReturn("审批通过通知");
                    when(rs.getString("content")).thenReturn("审批已通过");
                    when(rs.getString("payload")).thenReturn("{\"ticketId\":\"wo_test\"}");
                    when(rs.getString("status")).thenReturn("ACCEPTED");
                    when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(now));
                    when(rs.getTimestamp("accepted_at")).thenReturn(Timestamp.from(now));
                    when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
                    when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));
                    return List.of(mapper.mapRow(rs, 0));
                });
        NotificationEventService service = new NotificationEventService(jdbcTemplate, new ObjectMapper());

        PageResult<NotificationEventView> page = service.list(
                "APPROVAL_APPROVED",
                "wo_test",
                "u1001",
                "ACCEPTED",
                1,
                20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).payload()).containsEntry("ticketId", "wo_test");
    }
}
