import { describe, expect, it } from 'vitest'
import {
  buildLoginRedirectUrl,
  normalizeAuthToken,
  resolveAuthFailureReason,
} from './helpers'

describe('auth helpers', () => {
  it('normalizes non-empty bearer tokens', () => {
    expect(normalizeAuthToken(' token-123 ')).toBe('token-123')
    expect(normalizeAuthToken('   ')).toBeNull()
    expect(normalizeAuthToken(undefined)).toBeNull()
  })

  it('resolves auth failure reasons from SmartCS codes and HTTP status', () => {
    expect(resolveAuthFailureReason('1002')).toBe('expired')
    expect(resolveAuthFailureReason(undefined, 401)).toBe('expired')
    expect(resolveAuthFailureReason('1003')).toBe('forbidden')
    expect(resolveAuthFailureReason(undefined, 403)).toBe('forbidden')
    expect(resolveAuthFailureReason('0000', 200)).toBeNull()
  })

  it('builds login redirect url with current page and reason', () => {
    const url = buildLoginRedirectUrl({
      loginUrl: '/login',
      currentUrl: 'http://localhost:3000/chat?sessionId=s_1001',
      reason: 'expired',
    })

    expect(url).toBe(
      'http://localhost:3000/login?redirect=http%3A%2F%2Flocalhost%3A3000%2Fchat%3FsessionId%3Ds_1001&reason=expired',
    )
  })
})
