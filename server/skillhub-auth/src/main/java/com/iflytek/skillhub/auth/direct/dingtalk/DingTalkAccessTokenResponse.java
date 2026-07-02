package com.iflytek.skillhub.auth.direct.dingtalk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of the DingTalk v1 OAuth2 {@code /oauth2/accessToken} response we
 * actually consume. Any additional fields returned by DingTalk are ignored.
 *
 * <p>The {@code unionId} field is the cross-application stable identifier we
 * bind to a SkillHub user account.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DingTalkAccessTokenResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("expireIn") Long expireIn,
        @JsonProperty("unionId") String unionId,
        @JsonProperty("openId") String openId,
        @JsonProperty("nick") String nick,
        @JsonProperty("corpInfo") Object corpInfo
) {
}
