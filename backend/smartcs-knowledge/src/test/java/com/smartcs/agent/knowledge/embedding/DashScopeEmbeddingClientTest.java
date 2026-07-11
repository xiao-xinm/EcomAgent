package com.smartcs.agent.knowledge.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient.EmbeddingResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DashScopeEmbeddingClientTest {

    @Test
    void embedCallsCompatibleApiAndParsesVector() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeEmbeddingClient client = client(builder, "test-key", 3);
        server.expect(requestTo("https://embedding.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model": "text-embedding-v4",
                          "input": "退款多久到账",
                          "dimensions": 3,
                          "encoding_format": "float"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "data": [{"embedding": [0.1, -0.2, 0.3], "index": 0}],
                          "model": "text-embedding-v4",
                          "usage": {"prompt_tokens": 6}
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<EmbeddingResult> result = client.embed("退款多久到账");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().vector()).containsExactly(0.1D, -0.2D, 0.3D);
        assertThat(result.orElseThrow().promptTokens()).isEqualTo(6);
        server.verify();
    }

    @Test
    void embedReturnsEmptyWhenApiKeyIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeEmbeddingClient client = client(builder, "", 3);

        assertThat(client.embed("退款多久到账")).isEmpty();
        server.verify();
    }

    @Test
    void embedReturnsEmptyWhenDimensionsDoNotMatch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeEmbeddingClient client = client(builder, "test-key", 3);
        server.expect(requestTo("https://embedding.test/v1/embeddings"))
                .andRespond(withSuccess("""
                        {
                          "data": [{"embedding": [0.1, 0.2]}],
                          "model": "text-embedding-v4"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.embed("退款多久到账")).isEmpty();
        server.verify();
    }

    @Test
    void embedReturnsEmptyWhenRemoteServiceFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeEmbeddingClient client = client(builder, "test-key", 3);
        server.expect(requestTo("https://embedding.test/v1/embeddings"))
                .andRespond(withServerError());

        assertThat(client.embed("退款多久到账")).isEmpty();
        server.verify();
    }

    private DashScopeEmbeddingClient client(RestClient.Builder builder, String apiKey, int dimensions) {
        return new DashScopeEmbeddingClient(
                builder,
                new ObjectMapper(),
                "https://embedding.test/v1/",
                apiKey,
                "text-embedding-v4",
                dimensions);
    }
}
