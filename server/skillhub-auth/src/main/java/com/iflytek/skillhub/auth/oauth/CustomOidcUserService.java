package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Spring Security OIDC user-service bridge that normalizes standard OIDC
 * claims and reuses the existing OAuth login policy and identity binding flow.
 */
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final Logger log = LoggerFactory.getLogger(CustomOidcUserService.class);

    private final OAuthLoginFlowService oauthLoginFlowService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Autowired
    public CustomOidcUserService(OAuthLoginFlowService oauthLoginFlowService) {
        this(oauthLoginFlowService, new OidcUserService());
    }

    CustomOidcUserService(OAuthLoginFlowService oauthLoginFlowService,
                          OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.oauthLoginFlowService = oauthLoginFlowService;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        String registrationId = request.getClientRegistration().getRegistrationId();
        log.debug("OIDC login initiated for registration '{}'", registrationId);

        OidcUser upstreamUser = delegate.loadUser(request);
        OAuthClaims claims = toOAuthClaims(request, upstreamUser);
        log.debug("OIDC claims extracted - provider: {}, subject: {}, email present: {}, emailVerified: {}",
                claims.provider(), claims.subject(), claims.email() != null, claims.emailVerified());

        PlatformPrincipal principal;
        try {
            principal = oauthLoginFlowService.authenticate(claims);
        } catch (OAuth2AuthenticationException e) {
            log.warn("OIDC authentication failed for registration '{}', subject '{}': {}",
                    registrationId, claims.subject(), e.getMessage(), e);
            throw e;
        }
        log.debug("OIDC authentication succeeded - userId: {}, roles: {}",
                principal.userId(), principal.platformRoles());

        Map<String, Object> userInfoClaims = new HashMap<>(upstreamUser.getClaims());
        if (upstreamUser.getUserInfo() != null) {
            userInfoClaims.putAll(upstreamUser.getUserInfo().getClaims());
        }
        userInfoClaims.put("platformPrincipal", principal);
        userInfoClaims.put("providerLogin", principal.userId());

        var authorities = new LinkedHashSet<GrantedAuthority>(upstreamUser.getAuthorities());
        principal.platformRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);

        return new DefaultOidcUser(
                authorities,
                upstreamUser.getIdToken(),
                new OidcUserInfo(userInfoClaims),
                "providerLogin"
        );
    }

    static OAuthClaims toOAuthClaims(OidcUserRequest request, OidcUser oidcUser) {
        Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
        String subject = asString(claims.get("sub"));
        if (subject == null || subject.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_sub", "OIDC sub claim is required", null));
        }
        // Log the raw claim set so misconfigured OIDC providers (e.g. Casdoor
        // applications that only emit a bare subject) can be diagnosed without
        // a debugger. Single INFO line per login.
        if (log.isInfoEnabled()) {
            log.info("OIDC claims for registration '{}': {}",
                    request.getClientRegistration().getRegistrationId(), claims);
        }
        String email = asString(claims.get("email"));
        boolean emailVerified = Boolean.TRUE.equals(claims.get("email_verified"));
        if (!emailVerified) {
            email = null;
        }
        // Casdoor emits the user profile under a few different field names
        // depending on application configuration (displayName, name,
        // preferred_username, username). We try them in order before falling
        // back to the email local part and finally the OIDC subject.
        String providerLogin = firstPresent(
                asString(claims.get("displayName")),
                asString(claims.get("name")),
                asString(claims.get("preferred_username")),
                asString(claims.get("username")),
                emailLocalPart(email),
                subject
        );
        if (claims.get("picture") != null && claims.get("avatar_url") == null) {
            claims.put("avatar_url", claims.get("picture"));
        }

        return new OAuthClaims(
                request.getClientRegistration().getRegistrationId(),
                subject,
                email,
                emailVerified,
                providerLogin,
                claims
        );
    }

    private static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String str ? str : null;
    }
}
