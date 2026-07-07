package com.smartcs.agent.common.auth;

/**
 * Lightweight JWT settings shared by Gateway and Workbench.
 */
public record JwtAuthProperties(
        boolean enabled,
        String secret,
        String issuer,
        String audience
) {

    public static JwtAuthProperties disabled() {
        return new JwtAuthProperties(false, "", "", "");
    }

    public boolean hasSecret() {
        return hasText(secret);
    }

    public boolean hasIssuer() {
        return hasText(issuer);
    }

    public boolean hasAudience() {
        return hasText(audience);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
