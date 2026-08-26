import type { ReactNode } from 'react'
import { useAuthStore } from './authStore'
import type { Role } from '../types/auth'

interface RoleGateProps {
  allowed: Role[]
  children: ReactNode
}

export default function RoleGate({ allowed, children }: RoleGateProps) {
  const user = useAuthStore((state) => state.user)

  if (!user || !allowed.includes(user.role)) {
    return null
  }

  return <>{children}</>
}
