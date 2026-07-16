package com.smartcs.agent.workbench.realtime;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 从共享 MySQL 读取工单和坐席审计的增量变化，支持 Workbench 多实例独立订阅。 */
@Repository
@ConditionalOnProperty(name = "smartcs.realtime.ticket-events.enabled", havingValue = "true")
public class WorkbenchTicketChangeRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkbenchTicketChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    TicketChangeCursor currentCursor() {
        Timestamp databaseNow = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP(3)", Timestamp.class);
        if (databaseNow == null) {
            throw new IllegalStateException("无法读取 MySQL 当前时间");
        }
        return new TicketChangeCursor(databaseNow.toInstant(), "");
    }

    TicketChangeBatch findWorkOrderChanges(TicketChangeCursor cursor, int limit) {
        List<TicketChangedEvent> events = jdbcTemplate.query(
                """
                SELECT ticket_id, status, assigned_agent, updated_at
                FROM work_order
                WHERE updated_at > ?
                   OR (updated_at = ? AND ticket_id > ?)
                ORDER BY updated_at ASC, ticket_id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Instant changedAt = rs.getTimestamp("updated_at").toInstant();
                    String ticketId = rs.getString("ticket_id");
                    return new TicketChangedEvent(
                            "work-order:" + ticketId + ":" + changedAt.toEpochMilli(),
                            ticketId,
                            rs.getString("status"),
                            rs.getString("assigned_agent"),
                            changedAt);
                },
                Timestamp.from(cursor.changedAt()),
                Timestamp.from(cursor.changedAt()),
                cursor.tieBreaker(),
                limit);
        return new TicketChangeBatch(events, nextCursor(cursor, events, TicketChangedEvent::ticketId));
    }

    TicketChangeBatch findAuditChanges(TicketChangeCursor cursor, int limit) {
        List<AuditTicketChange> rows = jdbcTemplate.query(
                """
                SELECT a.event_id, a.ticket_id, w.status, w.assigned_agent, a.occurred_at
                FROM audit_log a
                INNER JOIN work_order w ON w.ticket_id = a.ticket_id
                WHERE a.ticket_id IS NOT NULL
                  AND (a.occurred_at > ?
                       OR (a.occurred_at = ? AND a.event_id > ?))
                ORDER BY a.occurred_at ASC, a.event_id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new AuditTicketChange(
                        rs.getString("event_id"),
                        new TicketChangedEvent(
                                rs.getString("event_id"),
                                rs.getString("ticket_id"),
                                rs.getString("status"),
                                rs.getString("assigned_agent"),
                                rs.getTimestamp("occurred_at").toInstant())),
                Timestamp.from(cursor.changedAt()),
                Timestamp.from(cursor.changedAt()),
                cursor.tieBreaker(),
                limit);
        List<TicketChangedEvent> events = rows.stream().map(AuditTicketChange::event).toList();
        TicketChangeCursor nextCursor = rows.isEmpty()
                ? cursor
                : new TicketChangeCursor(
                        rows.get(rows.size() - 1).event().changedAt(),
                        rows.get(rows.size() - 1).eventId());
        return new TicketChangeBatch(events, nextCursor);
    }

    private TicketChangeCursor nextCursor(
            TicketChangeCursor current,
            List<TicketChangedEvent> events,
            java.util.function.Function<TicketChangedEvent, String> tieBreaker) {
        if (events.isEmpty()) {
            return current;
        }
        TicketChangedEvent last = events.get(events.size() - 1);
        return new TicketChangeCursor(last.changedAt(), tieBreaker.apply(last));
    }

    private record AuditTicketChange(String eventId, TicketChangedEvent event) {
    }
}
