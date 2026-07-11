package com.smartcs.agent.knowledge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqItem;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqStatusRequest;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqUpsertRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexSynchronizer.IndexSyncSummary;
import com.smartcs.agent.knowledge.service.FaqKnowledgeService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FaqControllerTest {

    @Test
    void queryKeepsProvidedTraceIdAndReturnsFaqResponse() {
        FaqKnowledgeService faqKnowledgeService = mock(FaqKnowledgeService.class);
        FaqController controller = new FaqController(faqKnowledgeService);
        FaqQueryRequest request = new FaqQueryRequest("trace_faq", "s_test", "u1001", "h5", "退款多久到账");
        FaqQueryResponse faqResponse = new FaqQueryResponse(
                "faq_refund_arrival",
                "退款多久到账",
                "退款通常 1-3 个工作日到账。",
                true,
                0.92,
                List.of("退款", "到账"),
                "faq-keyword-v1",
                Instant.now());
        when(faqKnowledgeService.query(request)).thenReturn(faqResponse);

        ApiResponse<FaqQueryResponse> response = controller.query(request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isEqualTo("trace_faq");
        assertThat(response.data()).isSameAs(faqResponse);
        assertThat(response.data().matched()).isTrue();
        verify(faqKnowledgeService).query(request);
    }

    @Test
    void listFaqsReturnsPagedResult() {
        FaqKnowledgeService faqKnowledgeService = mock(FaqKnowledgeService.class);
        FaqController controller = new FaqController(faqKnowledgeService);
        PageResult<FaqItem> serviceResult = new PageResult<>(
                List.of(faqItem("faq_refund_arrival", "ACTIVE")),
                1,
                1,
                20);
        when(faqKnowledgeService.listFaqs("ACTIVE", "退款", 1, 20)).thenReturn(serviceResult);

        ApiResponse<PageResult<FaqItem>> response = controller.list("ACTIVE", "退款", 1, 20);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(serviceResult);
        assertThat(response.data().records()).hasSize(1);
        verify(faqKnowledgeService).listFaqs("ACTIVE", "退款", 1, 20);
    }

    @Test
    void createFaqReturnsCreatedItem() {
        FaqKnowledgeService faqKnowledgeService = mock(FaqKnowledgeService.class);
        FaqController controller = new FaqController(faqKnowledgeService);
        FaqUpsertRequest request = new FaqUpsertRequest(
                "优惠券过期了还能用吗",
                "优惠券过期后通常不能继续使用。",
                List.of("优惠券", "过期"),
                "promotion",
                "ACTIVE",
                10);
        FaqItem item = faqItem("faq_coupon_expired", "ACTIVE");
        when(faqKnowledgeService.createFaq(request)).thenReturn(item);

        ApiResponse<FaqItem> response = controller.create(request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(item);
        verify(faqKnowledgeService).createFaq(request);
    }

    @Test
    void updateStatusReturnsUpdatedItem() {
        FaqKnowledgeService faqKnowledgeService = mock(FaqKnowledgeService.class);
        FaqController controller = new FaqController(faqKnowledgeService);
        FaqStatusRequest request = new FaqStatusRequest("DISABLED");
        FaqItem item = faqItem("faq_refund_arrival", "DISABLED");
        when(faqKnowledgeService.updateStatus("faq_refund_arrival", request)).thenReturn(item);

        ApiResponse<FaqItem> response = controller.updateStatus("faq_refund_arrival", request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data().status()).isEqualTo("DISABLED");
        verify(faqKnowledgeService).updateStatus("faq_refund_arrival", request);
    }

    @Test
    void rebuildIndexReturnsOperationSummary() {
        FaqKnowledgeService faqKnowledgeService = mock(FaqKnowledgeService.class);
        FaqController controller = new FaqController(faqKnowledgeService);
        IndexSyncSummary summary = new IndexSyncSummary(true, 7, 16, 0, List.of());
        when(faqKnowledgeService.rebuildIndexes()).thenReturn(summary);

        ApiResponse<IndexSyncSummary> response = controller.rebuildIndex();

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(summary);
        assertThat(response.data().documentCount()).isEqualTo(7);
        verify(faqKnowledgeService).rebuildIndexes();
    }

    private FaqItem faqItem(String faqId, String status) {
        Instant now = Instant.now();
        return new FaqItem(
                faqId,
                "退款多久到账",
                "退款通常 1-3 个工作日到账。",
                List.of("退款", "到账"),
                "after_sale",
                status,
                100,
                now,
                now);
    }
}
