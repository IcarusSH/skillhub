import { redirect } from '@tanstack/react-router'

export type RouteLocationLike = {
  pathname: string
  searchStr?: string
  hash?: string
}

export function buildReturnTo(location: RouteLocationLike) {
  return `${location.pathname}${location.searchStr ?? ''}${location.hash ?? ''}`
}

/**
 * Provider-agnostic SSO entry path used by this fork of SkillHub.
 *
 * <p>The backend maps this path through Spring Security's
 * {@code oauth2Login} filter onto Casdoor's authorization endpoint. The
 * registration id is configured centrally so that swapping providers only
 * needs a yaml change — the SPA redirect target stays stable.
 */
const SSO_REGISTRATION_ID = 'casdoor'
const SSO_ENTRY_PATH = `/oauth2/authorization/${SSO_REGISTRATION_ID}`

/**
 * Effect that performs the actual browser navigation. Factored out so tests
 * can intercept it without having to redefine {@code window.location.replace}
 * (which is read-only in jsdom).
 *
 * <p>Uses {@code window.location.replace} rather than
 * {@code window.location.assign} so the original URL stays off the history
 * stack — back button after SSO completion should leave the private session,
 * not bounce through the SPA's pre-login state.
 */
export function redirectToSsoEntry(target: string): void {
  if (typeof window !== 'undefined') {
    window.location.replace(target)
  }
}

/**
 * Builds a `requireAuth` route guard.
 *
 * <p>When the current user cannot be resolved (no session cookie or
 * `/api/v1/auth/me` returned null), the guard issues a hard browser
 * navigation to the OAuth2 authorization endpoint with the original page
 * encoded as {@code returnTo}. Server-side SSO completes via Casdoor
 * (OIDC), then the OAuth2 success handler redirects back to the page.
 *
 * <p>Compared to a SPA-level {@code redirect('/login?returnTo=...')}, this:
 * <ul>
 *   <li>Avoids a flash of the legacy local-credentials login page.</li>
 *   <li>Works regardless of which OAuth provider is enabled — the backend's
 *       OIDC entry path is the single source of truth for IDP selection.</li>
 * </ul>
 */
export function createRequireAuth(
  getCurrentUser: () => Promise<unknown>,
  redirectFn: (target: string) => void = redirectToSsoEntry,
) {
  return async function requireAuth({ location }: { location: RouteLocationLike }) {
    const user = await getCurrentUser()
    if (!user) {
      if (typeof window !== 'undefined') {
        const returnTo = encodeURIComponent(buildReturnTo(location))
        redirectFn(`${SSO_ENTRY_PATH}?returnTo=${returnTo}`)
        // 用一个永不 resolve 的 promise 让 TanStack Router 挂着，避免路由继续往下走组件渲染
        return new Promise<never>(() => {})
      }
      // 仅作为理论上 SSR 兜底使用 —— SkillHub 是纯 SPA,这里几乎不会被执行。
      throw redirect({ to: '/login', search: { returnTo: buildReturnTo(location) } })
    }
    return { user }
  }
}
