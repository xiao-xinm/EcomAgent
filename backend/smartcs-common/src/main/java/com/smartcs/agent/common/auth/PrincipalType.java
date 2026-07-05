package com.smartcs.agent.common.auth;

import java.util.Locale;
import java.util.Optional;

/**
 * SmartCS principal categories used by customer chat and human workbench endpoints.
 */
public enum PrincipalType {

    CUSTOMER,
    AGENT,
    SUPERVISOR,
    ADMIN;

    public static Optional<PrincipalType> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PrincipalType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
