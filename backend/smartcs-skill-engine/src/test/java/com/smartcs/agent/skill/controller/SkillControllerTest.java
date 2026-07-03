package com.smartcs.agent.skill.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import com.smartcs.agent.skill.definition.SkillDtos.SkillExecuteRequest;
import com.smartcs.agent.skill.definition.SkillDtos.SkillExecuteResult;
import com.smartcs.agent.skill.executor.SkillExecutionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillControllerTest {

    @Test
    void executeReturnsSkillResultWithExecutionTrace() {
        SkillExecutionService skillExecutionService = mock(SkillExecutionService.class);
        SkillController controller = new SkillController(skillExecutionService);
        SkillExecuteRequest request = new SkillExecuteRequest(
                "trace_skill",
                "s_test",
                "m_test",
                "u1001",
                "order.query",
                "skill_order_query",
                RiskLevel.L0,
                RouteDecision.AUTO_REPLY,
                Map.of(),
                Map.of("channel", "h5"));
        SkillExecuteResult result = new SkillExecuteResult(
                "se_test",
                "trace_skill",
                "s_test",
                "m_test",
                "skill_order_query",
                "订单查询",
                "order.query",
                RiskLevel.L0,
                RouteDecision.AUTO_REPLY,
                "SUCCEEDED",
                Map.of("summary", "已完成订单查询。"),
                List.of(),
                "已完成订单查询。",
                Instant.now(),
                Instant.now());
        when(skillExecutionService.execute(request)).thenReturn(result);

        ApiResponse<SkillExecuteResult> response = controller.execute(request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isEqualTo("trace_skill");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().status()).isEqualTo("SUCCEEDED");
        verify(skillExecutionService).execute(request);
    }
}
