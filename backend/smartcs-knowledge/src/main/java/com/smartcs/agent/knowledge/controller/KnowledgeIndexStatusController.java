package com.smartcs.agent.knowledge.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.knowledge.dto.KnowledgeIndexStatusDtos.KnowledgeIndexStatus;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FAQ 混合检索索引状态接口。 */
@RestController
@RequestMapping("/api/knowledge/faq/index")
public class KnowledgeIndexStatusController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeIndexStatusController.class);

    private final KnowledgeIndexStatusService statusService;

    public KnowledgeIndexStatusController(KnowledgeIndexStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public ApiResponse<KnowledgeIndexStatus> status() {
        String traceId = TraceIds.newTraceId();
        KnowledgeIndexStatus status = statusService.inspect();
        LOGGER.info(
                "Knowledge index status checked traceId={} mode={} enabled={} healthy={} consistent={}",
                traceId,
                status.mode(),
                status.enabled(),
                status.healthy(),
                status.consistent());
        return ApiResponse.success(status, traceId);
    }
}
