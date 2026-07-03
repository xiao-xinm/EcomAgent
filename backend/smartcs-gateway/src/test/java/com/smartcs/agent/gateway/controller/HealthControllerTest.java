package com.smartcs.agent.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcs.agent.common.domain.ServiceHealth;
import com.smartcs.agent.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthReturnsGatewayServiceContract() {
        HealthController controller = new HealthController("smartcs-gateway", "test");

        ApiResponse<ServiceHealth> response = controller.health();

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.data().serviceName()).isEqualTo("smartcs-gateway");
        assertThat(response.data().status()).isEqualTo("UP");
        assertThat(response.data().activeProfile()).isEqualTo("test");
        assertThat(response.data().metadata()).containsEntry("module", "gateway");
    }
}
