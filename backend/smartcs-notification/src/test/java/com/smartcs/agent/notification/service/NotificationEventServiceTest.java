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
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationDeliveryResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationDeliveryResultRequest;
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
                    when(rs.getInt("retry_count")).thenReturn(0);
                    when(rs.getString("last_error")).thenReturn(null);
                    when(rs.getTimestamp("next_retry_at")).thenReturn(null);
                    when(rs.getTimestamp("delivered_at")).thenReturn(null);
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
        assertThat(page.records().get(0).retryCount()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recordFailedDeliveryIncrementsRetryAndStoresError() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(contains("retry_count = retry_count + 1"), any(Object[].class))).thenReturn(1);
        Instant nextRetryAt = Instant.parse("2026-07-05T01:10:00Z");
        mockDeliveryResultQuery(jdbcTemplate, "FAILED", 2, "站内信通道暂不可用", nextRetryAt, null);
        NotificationEventService service = new NotificationEventService(jdbcTemplate, new ObjectMapper());

        NotificationDeliveryResult result = service.recordDeliveryResult(
                "evt_test",
                new NotificationDeliveryResultRequest("FAILED", "站内信通道暂不可用", nextRetryAt));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.retryCount()).isEqualTo(2);
        assertThat(result.lastError()).isEqualTo("站内信通道暂不可用");
        assertThat(result.nextRetryAt()).isEqualTo(nextRetryAt);
        verify(jdbcTemplate).update(contains("retry_count = retry_count + 1"), any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recordDeliveredResultClearsErrorAndKeepsRetryCount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(contains("delivered_at = ?"), any(Object[].class))).thenReturn(1);
        Instant deliveredAt = Instant.parse("2026-07-05T01:10:00Z");
        mockDeliveryResultQuery(jdbcTemplate, "DELIVERED", 1, null, null, deliveredAt);
        NotificationEventService service = new NotificationEventService(jdbcTemplate, new ObjectMapper());

        NotificationDeliveryResult result = service.recordDeliveryResult(
                "evt_test",
                new NotificationDeliveryResultRequest("DELIVERED", null, null));

        assertThat(result.status()).isEqualTo("DELIVERED");
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.lastError()).isNull();
        assertThat(result.deliveredAt()).isEqualTo(deliveredAt);
        verify(jdbcTemplate).update(contains("delivered_at = ?"), any(Object[].class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockDeliveryResultQuery(
            JdbcTemplate jdbcTemplate,
            String status,
            int retryCount,
            String lastError,
            Instant nextRetryAt,
            Instant deliveredAt) {
        when(jdbcTemplate.queryForObject(
                contains("SELECT event_id, status, retry_count"),
                any(RowMapper.class),
                eq("evt_test")))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    Instant now = Instant.parse("2026-07-05T01:00:00Z");
                    when(rs.getString("event_id")).thenReturn("evt_test");
                    when(rs.getString("status")).thenReturn(status);
                    when(rs.getInt("retry_count")).thenReturn(retryCount);
                    when(rs.getString("last_error")).thenReturn(lastError);
                    when(rs.getTimestamp("next_retry_at")).thenReturn(nextRetryAt == null ? null : Timestamp.from(nextRetryAt));
                    when(rs.getTimestamp("delivered_at")).thenReturn(deliveredAt == null ? null : Timestamp.from(deliveredAt));
                    when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));
                    return mapper.mapRow(rs, 0);
                });
    }
}
