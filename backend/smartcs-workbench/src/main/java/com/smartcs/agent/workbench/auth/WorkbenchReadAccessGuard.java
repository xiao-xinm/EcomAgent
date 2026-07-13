package com.smartcs.agent.workbench.auth;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.auth.PrincipalType;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.workbench.ticket.WorkbenchOperationException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** 为新增只读控制器复用 Workbench 坐席身份规则，不改变既有工单鉴权链路。 */
@Component
public class WorkbenchReadAccessGuard {

    private final WorkbenchIdentityResolver identityResolver;

    public WorkbenchReadAccessGuard(WorkbenchIdentityResolver identityResolver) {
        this.identityResolver = identityResolver;
    }

    public AuthenticatedPrincipal requireOperator(HttpHeaders headers) {
        rejectInvalidStandardPrincipal(headers);
        AuthenticatedPrincipal principal = identityResolver.resolveAgent(headers, null);
        if (!principal.hasAnyRole(AuthRoles.AGENT, AuthRoles.SUPERVISOR, AuthRoles.ADMIN)) {
            throw forbidden("Current identity has no workbench role");
        }
        return principal;
    }

    private void rejectInvalidStandardPrincipal(HttpHeaders headers) {
        String principalId = firstHeader(headers, AuthHeaders.PRINCIPAL_ID);
        String principalType = firstHeader(headers, AuthHeaders.PRINCIPAL_TYPE);
        if (!hasText(principalId) && !hasText(principalType)) {
            return;
        }
        Optional<PrincipalType> parsedType = PrincipalType.parse(principalType);
        if (parsedType.isEmpty() || !isWorkbenchPrincipal(parsedType.get())) {
            throw forbidden("Current identity is not a workbench operator");
        }
    }

    private boolean isWorkbenchPrincipal(PrincipalType principalType) {
        return principalType == PrincipalType.AGENT
                || principalType == PrincipalType.SUPERVISOR
                || principalType == PrincipalType.ADMIN;
    }

    private String firstHeader(HttpHeaders headers, String name) {
        return headers == null ? null : headers.getFirst(name);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private WorkbenchOperationException forbidden(String message) {
        return new WorkbenchOperationException(ErrorCode.FORBIDDEN, message);
    }
}
