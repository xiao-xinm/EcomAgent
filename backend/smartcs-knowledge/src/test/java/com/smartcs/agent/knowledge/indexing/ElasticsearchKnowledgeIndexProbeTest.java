package com.smartcs.agent.knowledge.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexProbe.ProbeResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElasticsearchKnowledgeIndexProbeTest {

    @Test
    void probeCountsActiveDocumentsWithAuthentication() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKnowledgeIndexProbe probe = probe(builder, "elastic", "test-password");
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1/_count"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic ZWxhc3RpYzp0ZXN0LXBhc3N3b3Jk"))
                .andExpect(content().json("""
                        {"query":{"term":{"status":"ACTIVE"}}}
                        """))
                .andRespond(withSuccess("{\"count\":7}", MediaType.APPLICATION_JSON));

        ProbeResult result = probe.probe();

        assertThat(result.available()).isTrue();
        assertThat(result.documentCount()).isEqualTo(7L);
        assertThat(result.message()).isEqualTo("ready");
        server.verify();
    }

    @Test
    void probeReturnsUnavailableWhenElasticsearchRejectsRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKnowledgeIndexProbe probe = probe(builder, "elastic", "wrong-password");
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1/_count"))
                .andRespond(withUnauthorizedRequest());

        ProbeResult result = probe.probe();

        assertThat(result.available()).isFalse();
        assertThat(result.documentCount()).isNull();
        assertThat(result.message()).contains("401");
        server.verify();
    }

    private ElasticsearchKnowledgeIndexProbe probe(
            RestClient.Builder builder,
            String username,
            String password) {
        return new ElasticsearchKnowledgeIndexProbe(
                builder,
                new ObjectMapper(),
                "http://elasticsearch.test/",
                username,
                password,
                "smartcs_knowledge_faq_v1");
    }
}
