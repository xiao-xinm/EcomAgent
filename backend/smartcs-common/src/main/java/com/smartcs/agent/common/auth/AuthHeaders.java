package com.smartcs.agent.common.auth;

/**
 * Header names used while SmartCS migrates from body-based identity to trusted identity context.
 */
public final class AuthHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String PRINCIPAL_ID = "X-SmartCS-Principal-Id";
    public static final String PRINCIPAL_TYPE = "X-SmartCS-Principal-Type";
    public static final String ROLES = "X-SmartCS-Roles";
    public static final String PERMISSIONS = "X-SmartCS-Permissions";
    public static final String AUTH_SOURCE = "X-SmartCS-Auth-Source";

    public static final String DEV_USER_ID = "X-SmartCS-User-Id";
    public static final String DEV_OPERATOR_ID = "X-SmartCS-Operator-Id";

    private AuthHeaders() {
    }
}
