package com.smartcs.agent.common.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalized identity context. Services can build it from a token, trusted upstream header or dev fallback.
 */
public record AuthenticatedPrincipal(
        String principalId,
        PrincipalType principalType,
        Set<String> roles,
        Set<String> permissions,
        AuthSource authSource
) {

    public AuthenticatedPrincipal {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
        if (principalType == null) {
            throw new IllegalArgumentException("principalType must not be null");
        }
        if (authSource == null) {
            throw new IllegalArgumentException("authSource must not be null");
        }
        roles = normalize(roles);
        permissions = normalize(permissions);
    }

    public static AuthenticatedPrincipal customer(String userId, AuthSource authSource) {
        return new AuthenticatedPrincipal(userId, PrincipalType.CUSTOMER, Set.of(AuthRoles.CUSTOMER), Set.of(), authSource);
    }

    public static AuthenticatedPrincipal operator(
            String operatorId,
            PrincipalType principalType,
            Collection<String> roles,
            AuthSource authSource) {
        return new AuthenticatedPrincipal(operatorId, principalType, Set.copyOf(roles), Set.of(), authSource);
    }

    public static Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return normalize(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet()));
    }

    public String rolesCsv() {
        return String.join(",", roles);
    }

    public String permissionsCsv() {
        return String.join(",", permissions);
    }

    public boolean hasAnyRole(String... expectedRoles) {
        return Arrays.stream(expectedRoles)
                .map(AuthenticatedPrincipal::normalizeToken)
                .anyMatch(roles::contains);
    }

    private static Set<String> normalize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(AuthenticatedPrincipal::normalizeToken)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
