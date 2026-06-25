package com.smartcs.agent.knowledge.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import com.smartcs.agent.knowledge.service.FaqKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ query API used by Agent Core for knowledge questions.
 */
@RestController
@RequestMapping("/api/knowledge/faq")
public class FaqController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaqController.class);

    private final FaqKnowledgeService faqKnowledgeService;

    public FaqController(FaqKnowledgeService faqKnowledgeService) {
        this.faqKnowledgeService = faqKnowledgeService;
    }

    @PostMapping("/query")
    public ApiResponse<FaqQueryResponse> query(@RequestBody FaqQueryRequest request) {
        String traceId = request.traceId() == null || request.traceId().isBlank()
                ? TraceIds.newTraceId()
                : request.traceId();
        LOGGER.info(
                "FAQ query received traceId={} sessionId={} userId={} questionLength={}",
                traceId,
                request.sessionId(),
                request.userId(),
                request.question() == null ? 0 : request.question().length());
        FaqQueryResponse response = faqKnowledgeService.query(request);
        LOGGER.info(
                "FAQ query completed traceId={} answerId={} matched={} confidence={} keywords={}",
                traceId,
                response.answerId(),
                response.matched(),
                response.confidence(),
                response.matchedKeywords());
        return ApiResponse.success(response, traceId);
    }
}
