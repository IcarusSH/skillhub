import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { getDingTalkRuntimeConfig } from '@/api/client'

interface DingTalkLoginButtonProps {
  /**
   * The path on the same origin that will receive the `authCode` query
   * parameter after the user scans the QR code. Defaults to the public
   * runtime-config value, falling back to `/auth/dingtalk/callback`.
   */
  redirectPath?: string
  /**
   * Optional CSS class applied to the underlying button.
   */
  className?: string
}

/**
 * Builds the DingTalk scan-code authorization URL that the user's browser is
 * redirected to. Once the user scans with DingTalk, the configured
 * `redirect_uri` receives the `authCode` and our callback component finishes
 * the exchange by calling the direct-login API.
 *
 * Reference: https://open.dingtalk.com/document/orgapp/webapp-login-free-login
 */
function buildDingTalkAuthorizeUrl(appId: string, redirectUri: string, state: string): string {
  const url = new URL('https://oapi.dingtalk.com/connect/qrconnect')
  url.searchParams.set('appid', appId)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', 'snsapi_login')
  url.searchParams.set('redirect_uri', redirectUri)
  url.searchParams.set('state', state)
  return url.toString()
}

/**
 * Renders a "Login with DingTalk" button that triggers DingTalk's QR-code
 * scan flow. The actual credential exchange (authCode → unionId) happens on
 * the backend; the callback page calls `authApi.directLoginWithAuthCode`.
 */
export function DingTalkLoginButton({ redirectPath, className }: DingTalkLoginButtonProps) {
  const { t } = useTranslation()
  const config = getDingTalkRuntimeConfig()

  const handleClick = () => {
    if (!config.enabled || !config.appId) {
      // Surface a focused console diagnostic so the developer can fix the
      // runtime config without spelunking through React DevTools.
      // eslint-disable-next-line no-console
      console.error('[skillhub] DingTalk login is not configured.', {
        enabled: config.enabled,
        appIdPresent: !!config.appId,
        appIdLength: config.appId?.length ?? 0,
        hint:
          'Set SKILLHUB_WEB_AUTH_DINGTALK_ENABLED=true and SKILLHUB_WEB_AUTH_DINGTALK_APP_ID=<appid> ' +
          'in the container env, or update web/public/runtime-config.js in dev mode.',
      })
      return
    }
    const origin = typeof window !== 'undefined' ? window.location.origin : ''
    const path = redirectPath || config.redirectPath || '/auth/dingtalk/callback'
    const redirectUri = encodeURIComponent(`${origin}${path}`)
    // CSRF-protection for the OAuth style state. We just need it to round-trip
    // back from the DingTalk server so we can validate callback integrity.
    const state = Math.random().toString(36).slice(2, 14)
    if (typeof window !== 'undefined') {
      window.sessionStorage.setItem('skillhub.dingtalk.state', state)
    }
    const target = buildDingTalkAuthorizeUrl(config.appId, redirectUri, state)
    window.location.href = target
  }

  return (
    <Button
      type="button"
      variant="outline"
      className={className ?? 'w-full h-12 text-base'}
      onClick={handleClick}
      data-testid="dingtalk-login-button"
    >
      <svg className="w-5 h-5 mr-3" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M11.4 2.6c-4.6.4-8.4 4.4-8.4 9.1 0 1.8.5 3.6 1.4 5.1L3 21l4.4-1.4c1.4.7 3 1.1 4.6 1.1 4.9 0 9-4 9-9 0-4.9-3.9-8.7-9.6-8.1z" />
      </svg>
      {t('loginButton.dingtalk')}
    </Button>
  )
}
