import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from './authStore'
import type { Role } from '../types/auth'

interface ProtectedRouteProps {
  roles?: Role[]
}

export default function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (roles && (!user || !roles.includes(user.role))) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}
