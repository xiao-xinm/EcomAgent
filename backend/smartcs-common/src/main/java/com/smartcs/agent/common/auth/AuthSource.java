package com.smartcs.agent.common.auth;

/**
 * Identifies where the current identity came from during the Phase 8 compatibility rollout.
 */
public enum AuthSource {

    STANDARD_HEADER,
    DEV_HEADER,
    LEGACY_BODY,
    DEV_FALLBACK
}
