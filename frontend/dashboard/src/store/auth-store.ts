'use client'

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import Cookies from 'js-cookie'
import type { User, AuthResponse, AuthUserInfo } from '@/types'

// ─── State shape ──────────────────────────────────────────────────────────────

interface AuthState {
  user: User | null
  accessToken: string | null
  tenantId: string | null
  isAuthenticated: boolean
  isLoading: boolean
}

interface AuthActions {
  setAuth: (response: AuthResponse) => void
  syncUserProfile: (profile: AuthUserInfo) => void
  clearAuth: () => void
  setLoading: (loading: boolean) => void
}

type AuthStore = AuthState & AuthActions

// ─── Store ────────────────────────────────────────────────────────────────────

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      // initial state
      user: null,
      accessToken: null,
      tenantId: null,
      isAuthenticated: false,
      isLoading: false,

      // actions
      setAuth: (response: AuthResponse) => {
        // Persist tokens in cookies (httpOnly-style expiry)
        Cookies.set('accessToken', response.accessToken, {
          expires: 1,          // 1 day
          sameSite: 'strict',
        })
        Cookies.set('refreshToken', response.refreshToken, {
          expires: 7,          // 7 days
          sameSite: 'strict',
        })
        Cookies.set('tenantId', response.tenantId, {
          expires: 7,
          sameSite: 'strict',
        })

        set({
          user: response.user,
          accessToken: response.accessToken,
          tenantId: response.tenantId,
          isAuthenticated: true,
          isLoading: false,
        })
      },

      syncUserProfile: (profile: AuthUserInfo) => {
        set((state) => {
          const tenantId = state.user?.tenantId ?? state.tenantId

          if (!tenantId) {
            return {}
          }

          return {
            user: {
              ...profile,
              tenantId,
            },
            isAuthenticated: true,
            isLoading: false,
          }
        })
      },

      clearAuth: () => {
        Cookies.remove('accessToken')
        Cookies.remove('refreshToken')
        Cookies.remove('tenantId')

        set({
          user: null,
          accessToken: null,
          tenantId: null,
          isAuthenticated: false,
          isLoading: false,
        })
      },

      setLoading: (loading: boolean) => set({ isLoading: loading }),
    }),
    {
      name: 'bbss-auth-storage',
      storage: createJSONStorage(() =>
        typeof window !== 'undefined' ? localStorage : ({} as Storage)
      ),
      // Only persist non-sensitive state fields (tokens already in cookies)
      partialize: (state) => ({
        user: state.user,
        accessToken: state.accessToken,
        tenantId: state.tenantId,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)

// ─── Selectors ────────────────────────────────────────────────────────────────

export const selectUser = (s: AuthStore) => s.user
export const selectIsAuthenticated = (s: AuthStore) => s.isAuthenticated
export const selectTenantId = (s: AuthStore) => s.tenantId
export const selectIsSuperAdmin = (s: AuthStore) =>
  s.user?.roles.includes('ROLE_SUPER_ADMIN') ?? false
