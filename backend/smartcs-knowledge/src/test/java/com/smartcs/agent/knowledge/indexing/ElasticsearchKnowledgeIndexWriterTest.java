package com.smartcs.agent.knowledge.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElasticsearchKnowledgeIndexWriterTest {

    @Test
    void upsertCreatesMissingIndexAndWritesDocument() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKnowledgeIndexWriter writer = new ElasticsearchKnowledgeIndexWriter(
                builder,
                "http://elasticsearch.test/",
                "elastic",
                "test-password",
                "smartcs_knowledge_faq_v1");
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1"))
                .andExpect(method(HttpMethod.HEAD))
                .andExpect(header("Authorization", "Basic ZWxhc3RpYzp0ZXN0LXBhc3N3b3Jk"))
                .andRespond(withResourceNotFound());
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://elasticsearch.test/smartcs_knowledge_faq_v1/_doc/faq_refund_arrival?refresh=wait_for"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        KnowledgeIndexWriter.IndexOperationResult result = writer.upsert(document("ACTIVE"));

        assertThat(result.success()).isTrue();
        assertThat(result.writer()).isEqualTo("elasticsearch");
        server.verify();
    }

    @Test
    void resetDeletesAllDocumentsFromExistingIndex() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKnowledgeIndexWriter writer = new ElasticsearchKnowledgeIndexWriter(
                builder,
                "http://elasticsearch.test",
                "",
                "",
                "smartcs_knowledge_faq_v1");
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1"))
                .andRespond(withSuccess());
        server.expect(requestTo(
                        "http://elasticsearch.test/smartcs_knowledge_faq_v1/_delete_by_query?refresh=true&conflicts=proceed"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(writer.reset().success()).isTrue();
        server.verify();
    }

    private FaqIndexDocument document(String status) {
        return new FaqIndexDocument(
                "faq_refund_arrival",
                "退款多久到账",
                "退款通常会在审核后原路退回。",
                List.of("退款", "到账"),
                "after_sale",
                status,
                100,
                "abc123",
                Instant.parse("2026-07-11T07:00:00Z"));
    }
}
