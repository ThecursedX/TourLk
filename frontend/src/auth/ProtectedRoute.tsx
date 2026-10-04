import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from './authStore'
import type { Role } from '../types/auth'

interface ProtectedRouteProps {
  roles?: Role[]
}

export default function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)

  if (!isAuthenticated) {
    // Deliberately no `state.from` here: login always routes by the newly
    // authenticated user's role (see LoginPage/RegisterPage), never back to
    // whatever page was open before — that page may not even apply to them.
    return <Navigate to="/login" replace />
  }

  if (roles && (!user || !roles.includes(user.role))) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}
