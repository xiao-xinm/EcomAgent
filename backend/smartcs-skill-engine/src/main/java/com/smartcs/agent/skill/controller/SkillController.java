package com.smartcs.agent.skill.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.skill.definition.SkillDtos.SkillDefinitionView;
import com.smartcs.agent.skill.definition.SkillDtos.SkillExecuteRequest;
import com.smartcs.agent.skill.definition.SkillDtos.SkillExecuteResult;
import com.smartcs.agent.skill.executor.SkillExecutionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill registry query and execution endpoints.
 */
@RestController
public class SkillController {

    private final SkillExecutionService skillExecutionService;

    public SkillController(SkillExecutionService skillExecutionService) {
        this.skillExecutionService = skillExecutionService;
    }

    @GetMapping("/api/skills")
    public ApiResponse<List<SkillDefinitionView>> listSkills(
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(
                skillExecutionService.listSkills(intent, status, enabled),
                TraceIds.newTraceId());
    }

    @GetMapping("/api/skills/{skillId}")
    public ApiResponse<SkillDefinitionView> getSkill(@PathVariable String skillId) {
        return ApiResponse.success(skillExecutionService.getSkill(skillId), TraceIds.newTraceId());
    }

    @PostMapping("/api/skills/execute")
    public ApiResponse<SkillExecuteResult> execute(@Valid @RequestBody SkillExecuteRequest request) {
        SkillExecuteResult result = skillExecutionService.execute(request);
        return ApiResponse.success(result, result.traceId());
    }
}
