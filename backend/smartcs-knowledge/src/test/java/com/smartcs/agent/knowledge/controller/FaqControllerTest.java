package com.smartcs.agent.knowledge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import com.smartcs.agent.knowledge.service.FaqKnowledgeService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FaqControllerTest {

    @Test
    void queryKeepsProvidedTraceIdAndReturnsFaqResponse() {
        FaqKnowledgeService faqKnowledgeService = mock(FaqKnowledgeService.class);
        FaqController controller = new FaqController(faqKnowledgeService);
        FaqQueryRequest request = new FaqQueryRequest("trace_faq", "s_test", "u1001", "h5", "退款多久到");
        FaqQueryResponse faqResponse = new FaqQueryResponse(
                "faq_refund_time",
                "退款多久到",
                "退款通常 1-3 个工作日到账。",
                true,
                0.92,
                List.of("退款", "到账"),
                "builtin",
                Instant.now());
        when(faqKnowledgeService.query(request)).thenReturn(faqResponse);

        ApiResponse<FaqQueryResponse> response = controller.query(request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isEqualTo("trace_faq");
        assertThat(response.data()).isSameAs(faqResponse);
        assertThat(response.data().matched()).isTrue();
        verify(faqKnowledgeService).query(request);
    }
}
