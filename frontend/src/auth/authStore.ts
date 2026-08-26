import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { loginRequest, registerRequest } from '../api/authApi'
import type { LoginRequestDto, RegisterRequestDto, User } from '../types/auth'

interface AuthState {
  user: User | null
  token: string | null
  isAuthenticated: boolean
  login: (credentials: LoginRequestDto) => Promise<void>
  register: (data: RegisterRequestDto) => Promise<void>
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,

      login: async (credentials) => {
        const res = await loginRequest(credentials)
        set({
          user: { userId: res.userId, name: res.name, email: res.email, role: res.role },
          token: res.token,
          isAuthenticated: true,
        })
      },

      register: async (data) => {
        const res = await registerRequest(data)
        set({
          user: { userId: res.userId, name: res.name, email: res.email, role: res.role },
          token: res.token,
          isAuthenticated: true,
        })
      },

      logout: () => set({ user: null, token: null, isAuthenticated: false }),
    }),
    {
      name: 'tourlk-auth',
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
)
