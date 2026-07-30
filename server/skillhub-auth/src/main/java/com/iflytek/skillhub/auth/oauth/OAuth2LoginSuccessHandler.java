package com.iflytek.skillhub.auth.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login success handler that copies the resolved platform principal into the
 * HTTP session and then redirects to the stored return target or default URL.
 *
 * <p>The default target URL is an absolute URL built from
 * {@code skillhub.public.base-url} (env var {@code SKILLHUB_PUBLIC_BASE_URL}).
 * In production that value points at the externally reachable frontend host
 * (typically behind a reverse proxy), so the post-login redirect lands on
 * the SPA rather than on the backend's own port. The {@code returnTo}
 * parameter — when present in the session — is preferred over the default
 * target.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final PlatformSessionService platformSessionService;
    private final OAuthLoginFlowService oauthLoginFlowService;
    private final String publicBaseUrl;

    public OAuth2LoginSuccessHandler(PlatformSessionService platformSessionService,
                                     OAuthLoginFlowService oauthLoginFlowService,
                                     @Value("${skillhub.public.base-url:}") String publicBaseUrl) {
        this.platformSessionService = platformSessionService;
        this.oauthLoginFlowService = oauthLoginFlowService;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        // If we have an absolute public base URL, build the absolute default
        // target. Otherwise fall back to the existing relative default
        // (which Spring will resolve against the incoming request URL).
        if (!this.publicBaseUrl.isEmpty()) {
            setDefaultTargetUrl(this.publicBaseUrl + OAuthLoginRedirectSupport.DEFAULT_TARGET_URL);
        } else {
            setDefaultTargetUrl(OAuthLoginRedirectSupport.DEFAULT_TARGET_URL);
        }
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            PlatformPrincipal principal = (PlatformPrincipal) oAuth2User.getAttributes().get("platformPrincipal");
            if (principal != null) {
                platformSessionService.attachToAuthenticatedSession(principal, authentication, request);
                // Explicitly persist the SecurityContext via the repository so
                // Spring Session (Redis) writes down and the response carries
                // a Set-Cookie for the mutated session id. Without this, the
                // callback comes back without Set-Cookie (the SPI side sets
                // session attributes but never triggers the repository save),
                // and the browser keeps the old (unauthenticated) session id
                // — so the next /api/v1/auth/me returns 401 and the SPA
                // bounces the user back to the login page.
                SecurityContext context = SecurityContextHolder.getContext();
                if (context != null) {
                    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
                }
            }
        }
        String returnTo = oauthLoginFlowService.consumeReturnTo(request.getSession(false));
        // Self-referential guard: if the request was initiated from /login
        // itself (e.g. an outdated SPA bundle or a requireAuth guard that
        // fired on /login before this commit added /login to the public
        // route list), drop the returnTo so we fall through to the
        // configured default target instead of bouncing back to the login
        // page the user just came from.
        if (returnTo != null && "/login".equals(returnTo)) {
            returnTo = null;
        }
        // Translate a relative returnTo into an absolute one rooted at
        // publicBaseUrl, so the post-login redirect lands on the SPA host
        // (Vite / nginx) rather than the backend's own port.
        if (returnTo != null) {
            if (!publicBaseUrl.isEmpty() && returnTo.startsWith("/")) {
                returnTo = publicBaseUrl + returnTo;
            }
            getRedirectStrategy().sendRedirect(request, response, returnTo);
            clearAuthenticationAttributes(request);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
