package com.smartcs.agent.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElasticsearchKeywordRetrievalClientTest {

    @Test
    void searchUsesBasicAuthAndParsesRankedHits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKeywordRetrievalClient client = client(builder, "elastic", "test-password");
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic ZWxhc3RpYzp0ZXN0LXBhc3N3b3Jk"))
                .andExpect(content().json("""
                        {
                          "size": 2,
                          "track_total_hits": false,
                          "_source": ["faqId", "question", "answer", "category"],
                          "query": {
                            "bool": {
                              "filter": [{"term": {"status": "ACTIVE"}}],
                              "must": [{
                                "multi_match": {
                                  "query": "退款多久能到账",
                                  "fields": ["question^4", "keywords^3", "answer"],
                                  "type": "best_fields",
                                  "operator": "or"
                                }
                              }]
                            }
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "hits": {
                            "hits": [
                              {
                                "_id": "faq_refund_arrival",
                                "_score": 8.4,
                                "_source": {
                                  "faqId": "faq_refund_arrival",
                                  "question": "退款多久到账",
                                  "answer": "退款通常会在审核后原路退回。",
                                  "category": "after_sale"
                                }
                              },
                              {
                                "_id": "faq_return_policy",
                                "_score": 2.1,
                                "_source": {
                                  "faqId": "faq_return_policy",
                                  "question": "退货规则",
                                  "answer": "退货申请需要人工审核。",
                                  "category": "after_sale"
                                }
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<KnowledgeRetrievalCandidate> results = client.search("退款多久能到账", 2);

        assertThat(results).extracting(KnowledgeRetrievalCandidate::faqId)
                .containsExactly("faq_refund_arrival", "faq_return_policy");
        assertThat(results.get(0).source()).isEqualTo("elasticsearch-bm25-v1");
        assertThat(results.get(0).score()).isEqualTo(8.4D);
        server.verify();
    }

    @Test
    void searchSkipsIncompleteDocuments() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKeywordRetrievalClient client = client(builder, "", "");
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1/_search"))
                .andRespond(withSuccess("""
                        {
                          "hits": {
                            "hits": [{"_id": "broken", "_score": 1.0, "_source": {"faqId": "broken"}}]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.search("退款", 5)).isEmpty();
        server.verify();
    }

    @Test
    void searchReturnsEmptyWhenAuthenticationFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKeywordRetrievalClient client = client(builder, "elastic", "wrong-password");
        server.expect(requestTo("http://elasticsearch.test/smartcs_knowledge_faq_v1/_search"))
                .andRespond(withUnauthorizedRequest());

        assertThat(client.search("退款", 5)).isEmpty();
        server.verify();
    }

    @Test
    void searchDoesNotCallRemoteForBlankQuestion() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchKeywordRetrievalClient client = client(builder, "elastic", "test-password");

        assertThat(client.search(" ", 5)).isEmpty();
        server.verify();
    }

    private ElasticsearchKeywordRetrievalClient client(
            RestClient.Builder builder,
            String username,
            String password) {
        return new ElasticsearchKeywordRetrievalClient(
                builder,
                new ObjectMapper(),
                "http://elasticsearch.test/",
                username,
                password,
                "smartcs_knowledge_faq_v1");
    }
}
