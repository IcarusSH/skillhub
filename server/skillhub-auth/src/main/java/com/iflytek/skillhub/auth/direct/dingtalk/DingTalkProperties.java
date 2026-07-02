package com.iflytek.skillhub.auth.direct.dingtalk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the optional DingTalk scan-code direct-login provider.
 *
 * <p>All fields are env-driven; the provider bean is only registered when
 * {@code enabled} is {@code true}.
 */
@ConfigurationProperties(prefix = "skillhub.auth.direct.dingtalk")
public class DingTalkProperties {

    /**
     * Master switch. When {@code false}, the DingTalk provider bean is not
     * registered and {@code /api/v1/auth/methods} will not advertise it.
     */
    private boolean enabled = false;

    /**
     * AppKey (or AppId) for the enterprise app registered in DingTalk Open
     * Platform. Used in the OAuth2 access-token exchange.
     */
    private String appKey;

    /**
     * AppSecret paired with the {@code appKey}.
     */
    private String appSecret;

    /**
     * Override for the DingTalk API base URL. Defaults to
     * {@code https://api.dingtalk.com} (v1 OAuth2 access-token endpoint).
     */
    private String baseUrl = "https://api.dingtalk.com";

    /**
     * Maximum time (ms) to wait for the upstream access-token response before
     * failing the login with {@code 502 Bad Gateway}.
     */
    private long requestTimeoutMs = 5_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}
