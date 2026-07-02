package com.iflytek.skillhub.auth.direct.dingtalk;

import com.iflytek.skillhub.auth.direct.DirectAuthProvider;
import com.iflytek.skillhub.auth.direct.DirectAuthRequest;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Direct-auth provider that exchanges a browser-issued DingTalk scan-code
 * {@code authCode} for an access token and binds the resulting
 * {@code unionId} to a SkillHub user account.
 *
 * <p>Discovered by {@code DirectAuthService} via the {@link DirectAuthProvider}
 * SPI. {@code @ConditionalOnProperty} gates registration so a missing config
 * does not surface this provider as an option at all.
 */
@Component
@ConditionalOnProperty(prefix = "skillhub.auth.direct.dingtalk", name = "enabled", havingValue = "true")
public class DingTalkDirectAuthProvider implements DirectAuthProvider {

    static final String PROVIDER_CODE = "dingtalk";

    private final DingTalkAccessTokenClient accessTokenClient;
    private final IdentityBindingService identityBindingService;

    public DingTalkDirectAuthProvider(DingTalkAccessTokenClient accessTokenClient,
                                      IdentityBindingService identityBindingService) {
        this.accessTokenClient = accessTokenClient;
        this.identityBindingService = identityBindingService;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String displayName() {
        return "钉钉";
    }

    @Override
    public PlatformPrincipal authenticate(DirectAuthRequest request) {
        String authCode = request.authCode();
        if (authCode == null || authCode.isBlank()) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST,
                    "error.auth.direct.missingCredential", "authCode 不能为空");
        }

        DingTalkAccessTokenResponse token;
        try {
            token = accessTokenClient.exchangeAuthCode(authCode);
        } catch (DingTalkAccessTokenException ex) {
            throw new AuthFlowException(HttpStatus.BAD_GATEWAY,
                    "error.auth.dingtalk.upstream", ex.getMessage());
        }

        String unionId = token.unionId();
        Map<String, Object> claimsExtra = new HashMap<>();
        claimsExtra.put("open_id", token.openId());
        claimsExtra.put("nick", token.nick());
        claimsExtra.put("access_token", token.accessToken());

        String displayName = token.nick() != null && !token.nick().isBlank() ? token.nick() : unionId;

        OAuthClaims claims = new OAuthClaims(
                PROVIDER_CODE,
                unionId,
                /* email */ null,
                /* emailVerified */ false,
                displayName,
                claimsExtra
        );

        try {
            return identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE);
        } catch (RuntimeException ex) {
            throw new AuthFlowException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "error.auth.dingtalk.binding", "账号绑定失败: " + ex.getMessage());
        }
    }
}
