/** @vitest-environment jsdom */

import { describe, expect, it, vi } from 'vitest'
import { buildReturnTo, createRequireAuth, redirectToSsoEntry } from './auth-route'

describe('auth-route', () => {
  it('buildReturnTo preserves pathname search and hash', () => {
    expect(buildReturnTo({
      pathname: '/space/global/caldav-calendar',
      searchStr: '?tab=files',
      hash: '#readme',
    })).toBe('/space/global/caldav-calendar?tab=files#readme')
  })

  describe('createRequireAuth unauthenticated', () => {
    it('invokes the redirect effect with the SSO entry path + URL-encoded returnTo', async () => {
      const redirectSpy = vi.fn()
      const requireAuth = createRequireAuth(async () => null, redirectSpy)

      // 抑制 unhandled rejection 警告:requireAuth 内部返回 never-resolving promise,
      // 我们用 .catch 包一下,即便它永远不 reject 也不会污染测试报告。
      requireAuth({
        location: {
          pathname: '/space/global/caldav-calendar',
          searchStr: '?tab=files',
          hash: '#readme',
        },
      }).catch(() => {})

      // getCurrentUser 是 async,内部 await 后才调用 redirect effect;waitFor 给它一点时间。
      await vi.waitFor(() => {
        expect(redirectSpy).toHaveBeenCalledTimes(1)
      })
      expect(redirectSpy).toHaveBeenCalledWith(
        '/oauth2/authorization/casdoor?returnTo=%2Fspace%2Fglobal%2Fcaldav-calendar%3Ftab%3Dfiles%23readme',
      )
    })

    it('navigates with the bare path when no search or hash is present', async () => {
      const redirectSpy = vi.fn()
      const requireAuth = createRequireAuth(async () => null, redirectSpy)

      requireAuth({ location: { pathname: '/dashboard' } }).catch(() => {})

      await vi.waitFor(() => {
        expect(redirectSpy).toHaveBeenCalledTimes(1)
      })
      expect(redirectSpy).toHaveBeenCalledWith(
        '/oauth2/authorization/casdoor?returnTo=%2Fdashboard',
      )
    })

    it('returns a never-resolving promise so TanStack Router does not render after redirect', async () => {
      const redirectSpy = vi.fn()
      const requireAuth = createRequireAuth(async () => null, redirectSpy)

      const promise = requireAuth({ location: { pathname: '/dashboard' } }).catch(() => {})
      await vi.waitFor(() => {
        expect(redirectSpy).toHaveBeenCalledTimes(1)
      })

      // 100ms 内 promise 仍未 settle 即可;若意外 resolve 会立即触发 then。
      let settled = false
      promise.then(() => {
        settled = true
      })
      await new Promise<void>((resolve) => setTimeout(resolve, 100))
      expect(settled).toBe(false)
    })
  })

  it('createRequireAuth returns the current user when authenticated', async () => {
    const user = { userId: 'user-1' }
    const getCurrentUser = vi.fn(async () => user)
    const requireAuth = createRequireAuth(getCurrentUser)

    await expect(requireAuth({
      location: { pathname: '/dashboard' },
    })).resolves.toEqual({ user })
    expect(getCurrentUser).toHaveBeenCalledTimes(1)
  })

  describe('redirectToSsoEntry', () => {
    it('is exported and usable as a default redirect effect', () => {
      expect(typeof redirectToSsoEntry).toBe('function')
    })
  })
})
