import { useEffect } from 'react'
import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import {
  describeDingTalkCallbackError,
  useDingTalkCallbackOnMount,
} from '@/features/auth/use-dingtalk-callback'

/**
 * Page that receives the {@code authCode} query parameter delivered by DingTalk
 * after a user scans the QR code on the corporate login page.
 *
 * It triggers {@code /api/v1/auth/direct/login} with the DingTalk provider
 * code and, on success, navigates to the original target ({@code returnTo})
 * or the dashboard.
 */
export function DingTalkCallbackPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/auth/dingtalk/callback' })
  const authCode = search.code
  const state = search.state
  const callback = useDingTalkCallbackOnMount(authCode)

  useEffect(() => {
    if (callback.isSuccess) {
      const returnTo = (typeof search.returnTo === 'string' && search.returnTo.startsWith('/'))
        ? search.returnTo
        : '/dashboard'
      navigate({ to: returnTo })
    }
  }, [callback.isSuccess, search.returnTo, navigate])

  const expectedState = typeof window !== 'undefined'
    ? window.sessionStorage.getItem('skillhub.dingtalk.state')
    : null
  const stateMismatch =
    state !== undefined && expectedState !== null && state !== expectedState

  const loginSearch = { returnTo: '/' }

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <Card className="max-w-md p-8 space-y-4 text-center">
        <h1 className="text-2xl font-bold font-heading">
          {t('dingtalkCallback.title')}
        </h1>
        {stateMismatch ? (
          <div className="space-y-3 text-sm text-red-600">
            <p>{t('dingtalkCallback.stateMismatch')}</p>
            <Link to="/login" search={loginSearch}>
              <Button variant="outline">{t('dingtalkCallback.retry')}</Button>
            </Link>
          </div>
        ) : !authCode ? (
          <div className="space-y-3 text-sm text-muted-foreground">
            <p>{t('dingtalkCallback.missingCode')}</p>
            <Link to="/login" search={loginSearch}>
              <Button variant="outline">{t('dingtalkCallback.retry')}</Button>
            </Link>
          </div>
        ) : callback.isPending ? (
          <p className="text-sm text-muted-foreground">{t('dingtalkCallback.pending')}</p>
        ) : callback.isError ? (
          <div className="space-y-3 text-sm text-red-600">
            <p>{describeDingTalkCallbackError(callback.error)}</p>
            <Link to="/login" search={loginSearch}>
              <Button variant="outline">{t('dingtalkCallback.retry')}</Button>
            </Link>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">{t('dingtalkCallback.redirecting')}</p>
        )}
      </Card>
    </div>
  )
}
