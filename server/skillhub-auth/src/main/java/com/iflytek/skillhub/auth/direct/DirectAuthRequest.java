package com.iflytek.skillhub.auth.direct;

import java.util.Map;

/**
 * Credential bundle passed to a {@link DirectAuthProvider}.
 *
 * <p>For traditional username/password providers only the first two fields are
 * populated. Newer direct-auth flows (e.g. enterprise SSO scan-code login) use
 * {@code authCode} or {@code extraParams} to carry the upstream-issued token.
 */
public record DirectAuthRequest(
        String username,
        String password,
        String authCode,
        Map<String, String> extraParams
) {
    public DirectAuthRequest(String username, String password) {
        this(username, password, null, Map.of());
    }

    public DirectAuthRequest {
        if (extraParams == null) {
            extraParams = Map.of();
        }
    }
}
