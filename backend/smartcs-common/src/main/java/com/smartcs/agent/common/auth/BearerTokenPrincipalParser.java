package com.smartcs.agent.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;

/**
 * Parses signed Bearer JWT tokens into the SmartCS identity context.
 */
public class BearerTokenPrincipalParser {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_PRINCIPAL_TYPE = "principal_type";
    private static final String CLAIM_PRINCIPAL_TYPE_CAMEL = "principalType";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_SUBJECT = "sub";
    private static final String CLAIM_ISSUER = "iss";
    private static final String CLAIM_AUDIENCE = "aud";

    private final JwtAuthProperties properties;

    public BearerTokenPrincipalParser(JwtAuthProperties properties) {
        this.properties = properties == null ? JwtAuthProperties.disabled() : properties;
    }

    public Optional<AuthenticatedPrincipal> parse(
            String authorizationHeader,
            Set<PrincipalType> allowedTypes,
            PrincipalType defaultType,
            String fallbackRole) {
        Optional<String> token = bearerToken(authorizationHeader);
        if (token.isEmpty() || !properties.enabled()) {
            return Optional.empty();
        }
        if (!properties.hasSecret()) {
            throw new JwtPrincipalException("JWT secret is not configured");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token.get())
                    .getPayload();
            validateIssuer(claims);
            validateAudience(claims);
            return Optional.of(toPrincipal(claims, allowedTypes, defaultType, fallbackRole));
        } catch (JwtPrincipalException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtPrincipalException("JWT token is invalid", exception);
        }
    }

    private AuthenticatedPrincipal toPrincipal(
            Claims claims,
            Set<PrincipalType> allowedTypes,
            PrincipalType defaultType,
            String fallbackRole) {
        String principalId = stringClaim(claims, CLAIM_SUBJECT);
        if (!hasText(principalId)) {
            throw new JwtPrincipalException("JWT subject is missing");
        }
        Set<String> roles = tokensClaim(claims.get(CLAIM_ROLES));
        if (roles.isEmpty() && hasText(fallbackRole)) {
            roles = Set.of(fallbackRole);
        }
        PrincipalType principalType = resolvePrincipalType(claims, roles, defaultType);
        if (allowedTypes != null && !allowedTypes.isEmpty() && !allowedTypes.contains(principalType)) {
            throw new JwtPrincipalException("JWT principal type is not allowed");
        }
        return new AuthenticatedPrincipal(
                principalId,
                principalType,
                roles,
                tokensClaim(claims.get(CLAIM_PERMISSIONS)),
                AuthSource.BEARER_TOKEN);
    }

    private PrincipalType resolvePrincipalType(Claims claims, Set<String> roles, PrincipalType defaultType) {
        Optional<PrincipalType> explicitType = firstTypeClaim(claims);
        if (explicitType.isPresent()) {
            return explicitType.get();
        }
        Optional<PrincipalType> roleType = inferTypeFromRoles(roles);
        if (roleType.isPresent()) {
            return roleType.get();
        }
        if (defaultType != null) {
            return defaultType;
        }
        throw new JwtPrincipalException("JWT principal type is missing");
    }

    private Optional<PrincipalType> firstTypeClaim(Claims claims) {
        return firstNonBlank(
                singleString(claims.get(CLAIM_PRINCIPAL_TYPE)),
                singleString(claims.get(CLAIM_PRINCIPAL_TYPE_CAMEL)),
                singleString(claims.get(CLAIM_TYPE)))
                .flatMap(PrincipalType::parse);
    }

    private Optional<PrincipalType> inferTypeFromRoles(Set<String> roles) {
        if (roles.contains(AuthRoles.ADMIN)) {
            return Optional.of(PrincipalType.ADMIN);
        }
        if (roles.contains(AuthRoles.SUPERVISOR)) {
            return Optional.of(PrincipalType.SUPERVISOR);
        }
        if (roles.contains(AuthRoles.AGENT)) {
            return Optional.of(PrincipalType.AGENT);
        }
        if (roles.contains(AuthRoles.CUSTOMER)) {
            return Optional.of(PrincipalType.CUSTOMER);
        }
        return Optional.empty();
    }

    private void validateIssuer(Claims claims) {
        if (properties.hasIssuer() && !properties.issuer().equals(stringClaim(claims, CLAIM_ISSUER))) {
            throw new JwtPrincipalException("JWT issuer is invalid");
        }
    }

    private void validateAudience(Claims claims) {
        if (!properties.hasAudience()) {
            return;
        }
        Set<String> audiences = claims.getAudience() == null ? Set.of() : claims.getAudience();
        if (audiences.isEmpty()) {
            audiences = tokensClaim(claims.get(CLAIM_AUDIENCE));
        }
        if (!audiences.contains(properties.audience())) {
            throw new JwtPrincipalException("JWT audience is invalid");
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    private Optional<String> bearerToken(String authorizationHeader) {
        if (!hasText(authorizationHeader)) {
            return Optional.empty();
        }
        String value = authorizationHeader.trim();
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return hasText(token) ? Optional.of(token) : Optional.empty();
    }

    private Set<String> tokensClaim(Object claimValue) {
        if (claimValue == null) {
            return Set.of();
        }
        if (claimValue instanceof String value) {
            return AuthenticatedPrincipal.parseCsv(value);
        }
        if (claimValue instanceof Collection<?> values) {
            LinkedHashSet<String> tokens = new LinkedHashSet<>();
            for (Object value : values) {
                if (value != null) {
                    tokens.add(value.toString());
                }
            }
            return normalize(tokens);
        }
        if (claimValue instanceof Map<?, ?> values) {
            return normalize(values.values().stream()
                    .filter(value -> value != null)
                    .map(Object::toString)
                    .toList());
        }
        return AuthenticatedPrincipal.parseCsv(claimValue.toString());
    }

    private Set<String> normalize(Collection<String> values) {
        return AuthenticatedPrincipal.parseCsv(String.join(",", values));
    }

    private String stringClaim(Claims claims, String claimName) {
        if (CLAIM_SUBJECT.equals(claimName)) {
            return singleString(claims.getSubject());
        }
        if (CLAIM_ISSUER.equals(claimName)) {
            return singleString(claims.getIssuer());
        }
        return singleString(claims.get(claimName));
    }

    private String singleString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
