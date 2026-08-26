import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../auth/authStore'
import Button from '../components/ui/Button'

export default function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold text-slate-900">
        Welcome, {user?.name} ({user?.role})
      </h1>
      <p className="mt-2 text-slate-600">
        This is a placeholder dashboard. Each module's feature pages will link in here.
      </p>
      <Button variant="secondary" className="mt-6" onClick={handleLogout}>
        Logout
      </Button>
    </div>
  )
}
