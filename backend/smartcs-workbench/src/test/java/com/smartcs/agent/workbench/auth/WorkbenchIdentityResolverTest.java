package com.smartcs.agent.workbench.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcs.agent.common.auth.AuthPrincipalException;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthSource;
import com.smartcs.agent.common.auth.JwtAuthProperties;
import com.smartcs.agent.common.auth.JwtPrincipalException;
import com.smartcs.agent.common.auth.PrincipalType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WorkbenchIdentityResolverTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void bearerTokenTakesPrecedenceOverLegacyOperatorId() {
        WorkbenchIdentityResolver resolver = new WorkbenchIdentityResolver();
        resolver.configureJwtForTest(new JwtAuthProperties(true, SECRET, "", ""));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(Map.of(
                "sub", "agent_token",
                "principal_type", "SUPERVISOR",
                "roles", List.of("SUPERVISOR"))));

        var principal = resolver.resolveAgent(headers, "agent_body");

        assertThat(principal.principalId()).isEqualTo("agent_token");
        assertThat(principal.principalType()).isEqualTo(PrincipalType.SUPERVISOR);
        assertThat(principal.roles()).containsExactly(AuthRoles.SUPERVISOR);
        assertThat(principal.authSource()).isEqualTo(AuthSource.BEARER_TOKEN);
    }

    @Test
    void customerBearerTokenCannotFallbackToLegacyOperatorId() {
        WorkbenchIdentityResolver resolver = new WorkbenchIdentityResolver();
        resolver.configureJwtForTest(new JwtAuthProperties(true, SECRET, "", ""));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(Map.of(
                "sub", "u1001",
                "principal_type", "CUSTOMER",
                "roles", List.of("CUSTOMER"))));

        assertThatThrownBy(() -> resolver.resolveAgent(headers, "agent_body"))
                .isInstanceOf(JwtPrincipalException.class);
    }

    @Test
    void strictAuthRejectsLegacyOperatorIdFallback() {
        WorkbenchIdentityResolver resolver = new WorkbenchIdentityResolver();
        resolver.configureStrictAuthForTest(true);

        assertThatThrownBy(() -> resolver.resolveAgent(HttpHeaders.EMPTY, "agent_body"))
                .isInstanceOfSatisfying(AuthPrincipalException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1002"));
    }

    @Test
    void strictAuthRejectsTrustedOperatorMismatch() {
        WorkbenchIdentityResolver resolver = new WorkbenchIdentityResolver();
        resolver.configureJwtForTest(new JwtAuthProperties(true, SECRET, "", ""));
        resolver.configureStrictAuthForTest(true);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(Map.of(
                "sub", "agent_token",
                "principal_type", "AGENT",
                "roles", List.of("AGENT"))));

        assertThatThrownBy(() -> resolver.resolveAgent(headers, "agent_body"))
                .isInstanceOfSatisfying(AuthPrincipalException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1003"));
    }

    private String token(Map<String, Object> claims) {
        var builder = Jwts.builder();
        claims.forEach((name, value) -> applyClaim(builder, name, value));
        return builder
                .signWith(signingKey())
                .compact();
    }

    private void applyClaim(io.jsonwebtoken.JwtBuilder builder, String name, Object value) {
        if ("sub".equals(name)) {
            builder.subject(value.toString());
            return;
        }
        builder.claim(name, value);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
