package com.smartcs.agent.workbench.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.workbench.ticket.WorkbenchOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WorkbenchReadAccessGuardTest {

    @Test
    void developmentAgentIsAllowed() {
        WorkbenchReadAccessGuard guard = new WorkbenchReadAccessGuard(new WorkbenchIdentityResolver());
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_OPERATOR_ID, "agent_summary");
        headers.add(AuthHeaders.ROLES, AuthRoles.AGENT);

        assertThat(guard.requireOperator(headers).principalId()).isEqualTo("agent_summary");
    }

    @Test
    void customerStandardPrincipalCannotFallbackToDevelopmentAgent() {
        WorkbenchReadAccessGuard guard = new WorkbenchReadAccessGuard(new WorkbenchIdentityResolver());
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.PRINCIPAL_ID, "u1001");
        headers.add(AuthHeaders.PRINCIPAL_TYPE, "CUSTOMER");
        headers.add(AuthHeaders.ROLES, AuthRoles.CUSTOMER);

        assertThatThrownBy(() -> guard.requireOperator(headers))
                .isInstanceOfSatisfying(WorkbenchOperationException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1003"));
    }

    @Test
    void developmentOperatorWithoutWorkbenchRoleIsRejected() {
        WorkbenchReadAccessGuard guard = new WorkbenchReadAccessGuard(new WorkbenchIdentityResolver());
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_OPERATOR_ID, "agent_summary");
        headers.add(AuthHeaders.ROLES, AuthRoles.CUSTOMER);

        assertThatThrownBy(() -> guard.requireOperator(headers))
                .isInstanceOfSatisfying(WorkbenchOperationException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1003"));
    }
}
