package com.smartcs.agent.core.controller;

import com.smartcs.agent.common.domain.AgentReply;
import com.smartcs.agent.common.domain.ChatActionRequest;
import com.smartcs.agent.common.domain.ChatMessageView;
import com.smartcs.agent.common.domain.ChatRequest;
import com.smartcs.agent.common.domain.ChatSessionView;
import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.core.service.ChatSessionQueryService;
import com.smartcs.agent.core.service.MockAgentOrchestrator;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Core 内部聊天入口，由 Gateway 调用。
 */
@RestController
public class AgentChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentChatController.class);
    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";

    private final MockAgentOrchestrator mockAgentOrchestrator;
    private final ChatSessionQueryService chatSessionQueryService;

    public AgentChatController(
            MockAgentOrchestrator mockAgentOrchestrator,
            ChatSessionQueryService chatSessionQueryService) {
        this.mockAgentOrchestrator = mockAgentOrchestrator;
        this.chatSessionQueryService = chatSessionQueryService;
    }

    @PostMapping(
            value = "/api/agent/chat",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<AgentReply> chat(@Valid @RequestBody ChatRequest request) {
        LOGGER.info(
                "Agent Core收到聊天请求 traceId={} sessionId={} userId={} channel={}",
                request.traceId(),
                request.sessionId(),
                request.userId(),
                request.channel());
        AgentReply reply = mockAgentOrchestrator.process(request);
        LOGGER.info(
                "Agent Core生成回复 traceId={} sessionId={} replyId={} routeDecision={} riskLevel={} ticketId={}",
                reply.traceId(),
                reply.sessionId(),
                reply.replyId(),
                reply.routeDecision(),
                reply.riskLevel(),
                reply.ticketId());
        return ApiResponse.success(reply, reply.traceId());
    }

    @PostMapping(
            value = "/api/agent/actions",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<AgentReply> action(@Valid @RequestBody ChatActionRequest request) {
        LOGGER.info(
                "Agent Core收到聊天动作 traceId={} sessionId={} userId={} actionType={}",
                request.traceId(),
                request.sessionId(),
                request.userId(),
                request.actionType());
        AgentReply reply = mockAgentOrchestrator.processAction(request);
        LOGGER.info(
                "Agent Core生成动作回复 traceId={} sessionId={} replyId={} routeDecision={} riskLevel={}",
                reply.traceId(),
                reply.sessionId(),
                reply.replyId(),
                reply.routeDecision(),
                reply.riskLevel());
        return ApiResponse.success(reply, reply.traceId());
    }

    @GetMapping(value = "/api/agent/sessions/{sessionId}", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<ChatSessionView> getSession(@PathVariable String sessionId) {
        return chatSessionQueryService.findSession(sessionId)
                .map(session -> ApiResponse.success(session, session.traceId()))
                .orElseGet(() -> ApiResponse.failure(ErrorCode.NOT_FOUND, TraceIds.newTraceId()));
    }

    @GetMapping(value = "/api/agent/sessions/{sessionId}/messages", produces = APPLICATION_JSON_UTF8)
    public ApiResponse<List<ChatMessageView>> listMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "100") int limit) {
        return chatSessionQueryService.findSession(sessionId)
                .map(session -> ApiResponse.success(
                        chatSessionQueryService.listMessages(sessionId, limit),
                        session.traceId()))
                .orElseGet(() -> ApiResponse.failure(ErrorCode.NOT_FOUND, TraceIds.newTraceId()));
    }
}
