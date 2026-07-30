package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2LoginHandlersTest {

    @Test
    void successHandler_redirectsToStoredReturnTo() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                new com.iflytek.skillhub.auth.session.PlatformSessionService(),
                oauthLoginFlowService,
                "" // no public base url — keep relative redirect
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        String originalSessionId = session.getId();
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/dashboard/publish");

        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "github", Set.of()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("platformPrincipal", principal, "login", "user"), "login"),
                null,
                List.of()
        );

        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    HttpSession currentSession = invocation.getArgument(0);
                    Object value = currentSession.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    currentSession.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    return value;
                });

        handler.onAuthenticationSuccess(request, response, authentication);

        SecurityContext securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard/publish");
        assertThat(request.getSession(false).getId()).isEqualTo(originalSessionId);
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute("platformPrincipal")).isEqualTo(principal);
        assertThat(securityContext).isNotNull();
        assertThat(securityContext.getAuthentication().getPrincipal()).isEqualTo(principal);
    }

    /**
     * Regression test: when an unauthenticated client hits a protected API endpoint, Spring Security
     * caches that request. With {@code SavedRequestAwareAuthenticationSuccessHandler} the post-login
     * redirect would resolve to the cached API URL, leaving the user staring at raw JSON instead of
     * the dashboard. The handler must ignore the saved request and fall back to the default target.
     */
    @Test
    void successHandler_ignoresSavedApiRequestAndRedirectsToDefault() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                new com.iflytek.skillhub.auth.session.PlatformSessionService(),
                oauthLoginFlowService,
                ""
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/web/skills");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // Simulate Spring Security saving the API request that triggered login.
        new HttpSessionRequestCache().saveRequest(request, response);

        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "github", Set.of()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("platformPrincipal", principal, "login", "user"), "login"),
                null,
                List.of()
        );
        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
    }

    /**
     * Regression test: when the OAuth2 dance bounces back to {@code /login} as the
     * return target (typically because a {@code requireAuth} guard fired on
     * {@code /login} before it was added to the public-route list, or because an
     * older SPA bundle hard-coded {@code returnTo=/login}), the success handler
     * must treat that as "no return target" and fall through to the configured
     * default — otherwise the user ends up logged in but stuck on the login page.
     */
    @Test
    void successHandler_ignoresLoginSelfReturnTo() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                new com.iflytek.skillhub.auth.session.PlatformSessionService(),
                oauthLoginFlowService,
                ""
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // session already has /login as returnTo before the callback arrives
        request.getSession(true).setAttribute(
                OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/login");

        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "github", Set.of()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("platformPrincipal", principal, "login", "user"), "login"),
                null,
                List.of()
        );
        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenReturn("/login");

        handler.onAuthenticationSuccess(request, response, authentication);

        // /login must NOT be the redirect target — the configured default kicks in instead
        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @Test
    void failureHandler_redirectsBackToLoginWithReturnTo() throws Exception {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(oauthLoginFlowService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/settings/accounts");
        org.mockito.Mockito.when(oauthLoginFlowService.consumeReturnTo(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    HttpSession currentSession = invocation.getArgument(0);
                    Object value = currentSession.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    currentSession.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
                    return value;
                });
        org.mockito.Mockito.when(oauthLoginFlowService.resolveFailureRedirect(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("/settings/accounts")))
                .thenReturn("/login?returnTo=%2Fsettings%2Faccounts");

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_request"))
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?returnTo=%2Fsettings%2Faccounts");
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }
}
