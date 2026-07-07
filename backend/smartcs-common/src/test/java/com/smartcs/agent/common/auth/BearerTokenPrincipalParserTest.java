package com.smartcs.agent.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class BearerTokenPrincipalParserTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void parsesSignedCustomerBearerToken() {
        BearerTokenPrincipalParser parser = new BearerTokenPrincipalParser(
                new JwtAuthProperties(true, SECRET, "smartcs", "gateway"));
        String token = token(Map.of(
                "sub", "u1001",
                "iss", "smartcs",
                "aud", "gateway",
                "principal_type", "CUSTOMER",
                "roles", List.of("CUSTOMER")));

        AuthenticatedPrincipal principal = parser
                .parse("Bearer " + token, Set.of(PrincipalType.CUSTOMER), PrincipalType.CUSTOMER, AuthRoles.CUSTOMER)
                .orElseThrow();

        assertThat(principal.principalId()).isEqualTo("u1001");
        assertThat(principal.principalType()).isEqualTo(PrincipalType.CUSTOMER);
        assertThat(principal.roles()).containsExactly(AuthRoles.CUSTOMER);
        assertThat(principal.authSource()).isEqualTo(AuthSource.BEARER_TOKEN);
    }

    @Test
    void rejectsInvalidIssuer() {
        BearerTokenPrincipalParser parser = new BearerTokenPrincipalParser(
                new JwtAuthProperties(true, SECRET, "smartcs", ""));
        String token = token(Map.of(
                "sub", "u1001",
                "iss", "other",
                "principal_type", "CUSTOMER"));

        assertThatThrownBy(() -> parser.parse(
                "Bearer " + token,
                Set.of(PrincipalType.CUSTOMER),
                PrincipalType.CUSTOMER,
                AuthRoles.CUSTOMER))
                .isInstanceOf(JwtPrincipalException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void ignoresBearerTokenWhenJwtAuthIsDisabled() {
        BearerTokenPrincipalParser parser = new BearerTokenPrincipalParser(JwtAuthProperties.disabled());
        String token = token(Map.of(
                "sub", "u1001",
                "principal_type", "CUSTOMER"));

        assertThat(parser.parse(
                        "Bearer " + token,
                        Set.of(PrincipalType.CUSTOMER),
                        PrincipalType.CUSTOMER,
                        AuthRoles.CUSTOMER))
                .isEmpty();
    }

    private String token(Map<String, Object> claims) {
        var builder = Jwts.builder();
        claims.forEach((name, value) -> applyClaim(builder, name, value));
        return builder
                .signWith(signingKey())
                .compact();
    }

    @SuppressWarnings("deprecation")
    private void applyClaim(io.jsonwebtoken.JwtBuilder builder, String name, Object value) {
        if ("sub".equals(name)) {
            builder.subject(value.toString());
            return;
        }
        if ("iss".equals(name)) {
            builder.issuer(value.toString());
            return;
        }
        if ("aud".equals(name)) {
            builder.setAudience(value.toString());
            return;
        }
        builder.claim(name, value);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
