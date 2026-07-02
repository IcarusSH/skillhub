package com.iflytek.skillhub.auth.direct.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around the DingTalk v1 OAuth2 access-token endpoint.
 *
 * <p>This class owns no session state and is safe to share across requests.
 * Built on Spring's {@link RestClient} so it inherits the same HTTP
 * configuration as the rest of the platform (connect timeouts, proxy settings,
 * SSL bundle, etc.).
 */
@Component
public class DingTalkAccessTokenClient {

    private final DingTalkProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DingTalkAccessTokenClient(DingTalkProperties properties,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(buildRequestFactory(properties.getRequestTimeoutMs()))
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory buildRequestFactory(long timeoutMs) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);
        return factory;
    }

    /**
     * Exchanges a browser-issued {@code authCode} for an access token bound to
     * the configured corporate app.
     *
     * @throws DingTalkAccessTokenException on any non-2xx response, network
     *         failure, or empty {@code unionId}.
     */
    public DingTalkAccessTokenResponse exchangeAuthCode(String authCode) {
        if (authCode == null || authCode.isBlank()) {
            throw new DingTalkAccessTokenException("authCode 不能为空");
        }
        if (properties.getAppKey() == null || properties.getAppKey().isBlank()
                || properties.getAppSecret() == null || properties.getAppSecret().isBlank()) {
            throw new DingTalkAccessTokenException("skillhub.auth.direct.dingtalk.app-key / app-secret 未配置");
        }

        URI endpoint = URI.create(properties.getBaseUrl() + "/v1.0/oauth2/accessToken");
        Map<String, Object> body = Map.of(
                "appKey", properties.getAppKey(),
                "appSecret", properties.getAppSecret(),
                "authCode", authCode
        );

        try {
            DingTalkAccessTokenResponse response = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(DingTalkAccessTokenResponse.class);

            if (response == null || response.unionId() == null || response.unionId().isBlank()) {
                throw new DingTalkAccessTokenException("钉钉返回 unionId 为空");
            }
            return response;
        } catch (HttpClientErrorException ex) {
            throw new DingTalkAccessTokenException(
                    "钉钉 access-token 调用失败: HTTP " + ex.getStatusCode().value()
                            + " " + safeBody(ex));
        } catch (ResourceAccessException ex) {
            throw new DingTalkAccessTokenException("钉钉 access-token 调用超时或网络异常: " + ex.getMessage());
        }
    }

    private static String safeBody(HttpClientErrorException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null) {
            return "";
        }
        return body.length() > 256 ? body.substring(0, 256) + "..." : body;
    }
}
