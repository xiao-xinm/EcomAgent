package com.smartcs.agent.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthPrincipalException;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthSource;
import com.smartcs.agent.common.auth.JwtAuthProperties;
import com.smartcs.agent.common.auth.PrincipalType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class GatewayIdentityResolverTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void bearerTokenTakesPrecedenceOverDevelopmentAndLegacyIdentity() {
        GatewayIdentityResolver resolver = new GatewayIdentityResolver();
        resolver.configureJwtForTest(new JwtAuthProperties(true, SECRET, "", ""));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(Map.of(
                "sub", "u_token",
                "principal_type", "CUSTOMER",
                "roles", List.of("CUSTOMER"))));
        headers.add(AuthHeaders.DEV_USER_ID, "u_header");

        var principal = resolver.resolveCustomer(headers, "u_body").orElseThrow();

        assertThat(principal.principalId()).isEqualTo("u_token");
        assertThat(principal.principalType()).isEqualTo(PrincipalType.CUSTOMER);
        assertThat(principal.roles()).containsExactly(AuthRoles.CUSTOMER);
        assertThat(principal.authSource()).isEqualTo(AuthSource.BEARER_TOKEN);
    }

    @Test
    void strictAuthRejectsLegacyUserIdFallback() {
        GatewayIdentityResolver resolver = new GatewayIdentityResolver();
        resolver.configureStrictAuthForTest(true);

        assertThatThrownBy(() -> resolver.resolveCustomer(HttpHeaders.EMPTY, "u_body"))
                .isInstanceOfSatisfying(AuthPrincipalException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1002"));
    }

    @Test
    void strictAuthRejectsTrustedIdentityMismatch() {
        GatewayIdentityResolver resolver = new GatewayIdentityResolver();
        resolver.configureJwtForTest(new JwtAuthProperties(true, SECRET, "", ""));
        resolver.configureStrictAuthForTest(true);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(Map.of(
                "sub", "u_token",
                "principal_type", "CUSTOMER",
                "roles", List.of("CUSTOMER"))));

        assertThatThrownBy(() -> resolver.resolveCustomer(headers, "u_body"))
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
