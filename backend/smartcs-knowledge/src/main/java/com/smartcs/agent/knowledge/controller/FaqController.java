package com.smartcs.agent.knowledge.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqItem;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqStatusRequest;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqUpsertRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import com.smartcs.agent.knowledge.service.FaqKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ API used by Agent Core and the future knowledge management console.
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

    @GetMapping
    public ApiResponse<PageResult<FaqItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        String traceId = TraceIds.newTraceId();
        LOGGER.info(
                "FAQ list requested traceId={} status={} keyword={} pageNo={} pageSize={}",
                traceId,
                status,
                keyword,
                pageNo,
                pageSize);
        PageResult<FaqItem> result = faqKnowledgeService.listFaqs(status, keyword, pageNo, pageSize);
        LOGGER.info("FAQ list completed traceId={} total={}", traceId, result.total());
        return ApiResponse.success(result, traceId);
    }

    @PostMapping
    public ApiResponse<FaqItem> create(@RequestBody FaqUpsertRequest request) {
        String traceId = TraceIds.newTraceId();
        LOGGER.info(
                "FAQ create requested traceId={} questionLength={} keywordCount={}",
                traceId,
                request.question() == null ? 0 : request.question().length(),
                request.keywords() == null ? 0 : request.keywords().size());
        FaqItem result = faqKnowledgeService.createFaq(request);
        LOGGER.info("FAQ create completed traceId={} faqId={}", traceId, result.faqId());
        return ApiResponse.success(result, traceId);
    }

    @PutMapping("/{faqId}")
    public ApiResponse<FaqItem> update(
            @PathVariable String faqId,
            @RequestBody FaqUpsertRequest request) {
        String traceId = TraceIds.newTraceId();
        LOGGER.info(
                "FAQ update requested traceId={} faqId={} questionLength={} keywordCount={}",
                traceId,
                faqId,
                request.question() == null ? 0 : request.question().length(),
                request.keywords() == null ? 0 : request.keywords().size());
        FaqItem result = faqKnowledgeService.updateFaq(faqId, request);
        LOGGER.info("FAQ update completed traceId={} faqId={}", traceId, result.faqId());
        return ApiResponse.success(result, traceId);
    }

    @PostMapping("/{faqId}/status")
    public ApiResponse<FaqItem> updateStatus(
            @PathVariable String faqId,
            @RequestBody FaqStatusRequest request) {
        String traceId = TraceIds.newTraceId();
        LOGGER.info(
                "FAQ status update requested traceId={} faqId={} status={}",
                traceId,
                faqId,
                request.status());
        FaqItem result = faqKnowledgeService.updateStatus(faqId, request);
        LOGGER.info(
                "FAQ status update completed traceId={} faqId={} status={}",
                traceId,
                result.faqId(),
                result.status());
        return ApiResponse.success(result, traceId);
    }
}
