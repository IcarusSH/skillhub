package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body for {@code POST /api/v1/auth/direct/login}.
 *
 * <p>For local-account login only {@code username} and {@code password} are
 * required. For provider flows that surface an upstream-issued token (such as
 * an enterprise SSO scan-code flow) the request must carry either
 * {@code authCode} or a non-empty {@code extraParams}.
 */
public record DirectLoginRequest(
        @NotBlank(message = "认证提供方不能为空")
        String provider,

        @Size(max = 256)
        String username,

        @Size(max = 256)
        String password,

        @Size(max = 512)
        String authCode,

        Map<String, String> extraParams
) {

    @AssertTrue(message = "local 提供方必须提供 username 与 password")
    public boolean isLocalCredentialComplete() {
        if (!"local".equals(provider)) {
            return true;
        }
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    @AssertTrue(message = "credential 不能为空")
    public boolean hasCredential() {
        if ("local".equals(provider)) {
            return true;
        }
        boolean hasAuthCode = authCode != null && !authCode.isBlank();
        boolean hasExtras = extraParams != null && !extraParams.isEmpty();
        return hasAuthCode || hasExtras;
    }
}
