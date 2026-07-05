package com.smartcs.agent.workbench.auth;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthSource;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.auth.PrincipalType;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Resolves operator identity while Workbench still accepts legacy operatorId request bodies.
 */
@Component
public class WorkbenchIdentityResolver {

    private static final String DEV_FALLBACK_OPERATOR_ID = "agent_001";

    public AuthenticatedPrincipal resolveAgent(HttpHeaders headers, String legacyOperatorId) {
        Optional<AuthenticatedPrincipal> standardPrincipal = resolveStandardAgent(headers);
        if (standardPrincipal.isPresent()) {
            return standardPrincipal.get();
        }
        String devOperatorId = firstHeader(headers, AuthHeaders.DEV_OPERATOR_ID);
        if (hasText(devOperatorId)) {
            return AuthenticatedPrincipal.operator(
                    devOperatorId,
                    PrincipalType.AGENT,
                    rolesOr(headers, AuthRoles.AGENT),
                    AuthSource.DEV_HEADER);
        }
        if (hasText(legacyOperatorId)) {
            return AuthenticatedPrincipal.operator(
                    legacyOperatorId,
                    PrincipalType.AGENT,
                    Set.of(AuthRoles.AGENT),
                    AuthSource.LEGACY_BODY);
        }
        return AuthenticatedPrincipal.operator(
                DEV_FALLBACK_OPERATOR_ID,
                PrincipalType.AGENT,
                Set.of(AuthRoles.AGENT),
                AuthSource.DEV_FALLBACK);
    }

    private Optional<AuthenticatedPrincipal> resolveStandardAgent(HttpHeaders headers) {
        String principalId = firstHeader(headers, AuthHeaders.PRINCIPAL_ID);
        Optional<PrincipalType> principalType = PrincipalType.parse(firstHeader(headers, AuthHeaders.PRINCIPAL_TYPE));
        if (!hasText(principalId) || principalType.isEmpty() || !isWorkbenchPrincipal(principalType.get())) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedPrincipal(
                principalId,
                principalType.get(),
                rolesOr(headers, defaultRole(principalType.get())),
                AuthenticatedPrincipal.parseCsv(firstHeader(headers, AuthHeaders.PERMISSIONS)),
                AuthSource.STANDARD_HEADER));
    }

    private boolean isWorkbenchPrincipal(PrincipalType principalType) {
        return principalType == PrincipalType.AGENT
                || principalType == PrincipalType.SUPERVISOR
                || principalType == PrincipalType.ADMIN;
    }

    private String defaultRole(PrincipalType principalType) {
        return switch (principalType) {
            case SUPERVISOR -> AuthRoles.SUPERVISOR;
            case ADMIN -> AuthRoles.ADMIN;
            default -> AuthRoles.AGENT;
        };
    }

    private Set<String> rolesOr(HttpHeaders headers, String fallbackRole) {
        Set<String> roles = AuthenticatedPrincipal.parseCsv(firstHeader(headers, AuthHeaders.ROLES));
        return roles.isEmpty() ? Set.of(fallbackRole) : roles;
    }

    private String firstHeader(HttpHeaders headers, String name) {
        return headers == null ? null : headers.getFirst(name);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
