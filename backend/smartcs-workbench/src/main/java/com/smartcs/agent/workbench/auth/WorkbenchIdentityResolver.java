package com.smartcs.agent.workbench.auth;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthPrincipalException;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthSource;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.auth.BearerTokenPrincipalParser;
import com.smartcs.agent.common.auth.JwtAuthProperties;
import com.smartcs.agent.common.auth.PrincipalType;
import com.smartcs.agent.common.enums.ErrorCode;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Resolves operator identity while Workbench still accepts legacy operatorId request bodies.
 */
@Component
public class WorkbenchIdentityResolver {

    private static final String DEV_FALLBACK_OPERATOR_ID = "agent_001";

    @Value("${smartcs.auth.jwt.enabled:false}")
    private boolean jwtEnabled;

    @Value("${smartcs.auth.jwt.secret:}")
    private String jwtSecret;

    @Value("${smartcs.auth.jwt.issuer:}")
    private String jwtIssuer;

    @Value("${smartcs.auth.jwt.audience:}")
    private String jwtAudience;

    @Value("${smartcs.auth.strict-enabled:false}")
    private boolean strictAuthEnabled;

    public AuthenticatedPrincipal resolveAgent(HttpHeaders headers, String legacyOperatorId) {
        Optional<AuthenticatedPrincipal> bearerPrincipal = resolveBearerAgent(headers);
        if (bearerPrincipal.isPresent()) {
            return trustedAgent(bearerPrincipal.get(), legacyOperatorId);
        }
        Optional<AuthenticatedPrincipal> standardPrincipal = resolveStandardAgent(headers);
        if (standardPrincipal.isPresent()) {
            return trustedAgent(standardPrincipal.get(), legacyOperatorId);
        }
        if (strictAuthEnabled) {
            throw new AuthPrincipalException(ErrorCode.UNAUTHORIZED, "Workbench authentication is required");
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

    void configureJwtForTest(JwtAuthProperties properties) {
        JwtAuthProperties safeProperties = properties == null ? JwtAuthProperties.disabled() : properties;
        this.jwtEnabled = safeProperties.enabled();
        this.jwtSecret = safeProperties.secret();
        this.jwtIssuer = safeProperties.issuer();
        this.jwtAudience = safeProperties.audience();
    }

    void configureStrictAuthForTest(boolean strictAuthEnabled) {
        this.strictAuthEnabled = strictAuthEnabled;
    }

    private AuthenticatedPrincipal trustedAgent(AuthenticatedPrincipal principal, String legacyOperatorId) {
        if (strictAuthEnabled && hasText(legacyOperatorId) && !principal.principalId().equals(legacyOperatorId)) {
            throw new AuthPrincipalException(ErrorCode.FORBIDDEN, "Request operatorId does not match trusted identity");
        }
        return principal;
    }

    private Optional<AuthenticatedPrincipal> resolveBearerAgent(HttpHeaders headers) {
        // Workbench Token 必须解析成坐席类身份，不能从请求体 operatorId 降级绕过。
        return tokenParser().parse(
                firstHeader(headers, AuthHeaders.AUTHORIZATION),
                Set.of(PrincipalType.AGENT, PrincipalType.SUPERVISOR, PrincipalType.ADMIN),
                null,
                null);
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

    private BearerTokenPrincipalParser tokenParser() {
        return new BearerTokenPrincipalParser(new JwtAuthProperties(jwtEnabled, jwtSecret, jwtIssuer, jwtAudience));
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
