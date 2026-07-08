package com.iflytek.skillhub.auth.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provider-specific claims extractor for Casdoor (an OIDC identity provider
 * that fronts enterprise social logins such as DingTalk).
 *
 * <p>Casdoor's userinfo endpoint returns the standard OIDC subject plus a
 * small set of profile fields. Field names follow Casdoor's own convention
 * rather than the strict OIDC core set, so we look at {@code displayName}
 * first, then {@code name}, then {@code preferred_username}, then the email
 * local-part before falling back to the subject. The full raw attribute map
 * is preserved on {@code OAuthClaims.extra()} so downstream callers can
 * access the avatar URL or phone number.
 */
@Component
public class CasdoorClaimsExtractor implements OAuthClaimsExtractor {

    private static final Logger log = LoggerFactory.getLogger(CasdoorClaimsExtractor.class);

    @Override
    public String getProvider() {
        return "casdoor";
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        // Log raw attributes once per login so that misconfigured Casdoor apps
        // (which only return the bare subject) can be diagnosed without
        // re-attaching a debugger.
        if (log.isInfoEnabled()) {
            log.info("Casdoor userinfo attributes: {}", attrs);
        }

        String subject = asString(attrs.get("sub"));
        String displayName = firstPresent(
                asString(attrs.get("displayName")),
                asString(attrs.get("name")),
                asString(attrs.get("preferred_username")),
                asString(attrs.get("username")),
                emailLocalPart(asString(attrs.get("email"))),
                subject
        );
        String email = asString(attrs.get("email"));
        boolean emailVerified = Boolean.TRUE.equals(attrs.get("email_verified"));
        if (!emailVerified) {
            email = null;
        }

        return new OAuthClaims("casdoor", subject, email, emailVerified, displayName, attrs);
    }

    private static String asString(Object value) {
        return value instanceof String str ? str : null;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
