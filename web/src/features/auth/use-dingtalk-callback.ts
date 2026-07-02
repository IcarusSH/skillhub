import { useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi, getDingTalkRuntimeConfig } from '@/api/client'
import { ApiError } from '@/shared/lib/api-error'
import { clearSessionScopedQueries } from '@/features/notification/notification-session'

/**
 * Mutation hook used by the DingTalk scan-code callback page.
 *
 * Posts the authCode returned by DingTalk to {@code
 * /api/v1/auth/direct/login} using the {@code dingtalk} provider code, then
 * mirrors the local-login success path so the UI Session correctly caches the
 * newly authenticated user.
 */
export function useDingTalkCallback() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (authCode: string) =>
      authApi.directLoginWithAuthCode(getDingTalkRuntimeConfig().provider ?? 'dingtalk', {
        authCode,
      }),
    onSuccess: (user) => {
      clearSessionScopedQueries(queryClient)
      queryClient.setQueryData(['auth', 'me'], user)
    },
  })

  return mutation
}

/**
 * Convenience effect that triggers {@link useDingTalkCallback} when the
 * callback component mounts; returns the mutation so callers can also drive
 * the exchange on demand.
 */
export function useDingTalkCallbackOnMount(authCode: string | undefined) {
  const callback = useDingTalkCallback()
  useEffect(() => {
    if (authCode && !callback.isPending && !callback.isSuccess && !callback.isError) {
      callback.mutate(authCode)
    }
    // We intentionally depend only on authCode; the mutation instance is stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authCode])
  return callback
}

/**
 * Formats an {@link ApiError} thrown from the DingTalk callback into a
 * short string for inline display in the page. Avoids exposing upstream
 * details verbatim.
 */
export function describeDingTalkCallbackError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.serverMessage ?? error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return '登录失败，请重试'
}
