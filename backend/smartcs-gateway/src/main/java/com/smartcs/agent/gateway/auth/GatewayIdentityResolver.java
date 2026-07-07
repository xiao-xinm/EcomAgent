package com.smartcs.agent.gateway.auth;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthSource;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.auth.BearerTokenPrincipalParser;
import com.smartcs.agent.common.auth.JwtAuthProperties;
import com.smartcs.agent.common.auth.PrincipalType;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Resolves customer identity for the Phase 8 compatibility period.
 */
@Component
public class GatewayIdentityResolver {

    @Value("${smartcs.auth.jwt.enabled:false}")
    private boolean jwtEnabled;

    @Value("${smartcs.auth.jwt.secret:}")
    private String jwtSecret;

    @Value("${smartcs.auth.jwt.issuer:}")
    private String jwtIssuer;

    @Value("${smartcs.auth.jwt.audience:}")
    private String jwtAudience;

    public Optional<AuthenticatedPrincipal> resolveCustomer(HttpHeaders headers, String legacyUserId) {
        Optional<AuthenticatedPrincipal> bearerPrincipal = resolveBearerCustomer(headers);
        if (bearerPrincipal.isPresent()) {
            return bearerPrincipal;
        }
        Optional<AuthenticatedPrincipal> standardPrincipal = resolveStandardCustomer(headers);
        if (standardPrincipal.isPresent()) {
            return standardPrincipal;
        }
        String devUserId = firstHeader(headers, AuthHeaders.DEV_USER_ID);
        if (hasText(devUserId)) {
            Set<String> roles = rolesOr(headers, AuthRoles.CUSTOMER);
            return Optional.of(new AuthenticatedPrincipal(
                    devUserId,
                    PrincipalType.CUSTOMER,
                    roles,
                    Set.of(),
                    AuthSource.DEV_HEADER));
        }
        if (hasText(legacyUserId)) {
            return Optional.of(AuthenticatedPrincipal.customer(legacyUserId, AuthSource.LEGACY_BODY));
        }
        return Optional.empty();
    }

    void configureJwtForTest(JwtAuthProperties properties) {
        JwtAuthProperties safeProperties = properties == null ? JwtAuthProperties.disabled() : properties;
        this.jwtEnabled = safeProperties.enabled();
        this.jwtSecret = safeProperties.secret();
        this.jwtIssuer = safeProperties.issuer();
        this.jwtAudience = safeProperties.audience();
    }

    private Optional<AuthenticatedPrincipal> resolveBearerCustomer(HttpHeaders headers) {
        // Bearer Token 是生产接入点；默认关闭，避免影响当前本地开发兼容链路。
        return tokenParser().parse(
                firstHeader(headers, AuthHeaders.AUTHORIZATION),
                Set.of(PrincipalType.CUSTOMER),
                PrincipalType.CUSTOMER,
                AuthRoles.CUSTOMER);
    }

    private Optional<AuthenticatedPrincipal> resolveStandardCustomer(HttpHeaders headers) {
        String principalId = firstHeader(headers, AuthHeaders.PRINCIPAL_ID);
        Optional<PrincipalType> principalType = PrincipalType.parse(firstHeader(headers, AuthHeaders.PRINCIPAL_TYPE));
        if (!hasText(principalId) || principalType.isEmpty() || principalType.get() != PrincipalType.CUSTOMER) {
            return Optional.empty();
        }
        Set<String> roles = rolesOr(headers, AuthRoles.CUSTOMER);
        Set<String> permissions = AuthenticatedPrincipal.parseCsv(firstHeader(headers, AuthHeaders.PERMISSIONS));
        return Optional.of(new AuthenticatedPrincipal(
                principalId,
                PrincipalType.CUSTOMER,
                roles,
                permissions,
                AuthSource.STANDARD_HEADER));
    }

    private BearerTokenPrincipalParser tokenParser() {
        return new BearerTokenPrincipalParser(new JwtAuthProperties(jwtEnabled, jwtSecret, jwtIssuer, jwtAudience));
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
