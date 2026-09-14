import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../auth/authStore'
import RoleGate from '../auth/RoleGate'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'

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

      <RoleGate allowed={['ADMIN', 'GUIDE']}>
        <Card className="mt-8 max-w-md">
          <h2 className="font-semibold text-slate-900">Tour Packages</h2>
          <p className="mt-1 text-sm text-slate-600">
            Create and manage the tour packages you offer.
          </p>
          <Link to="/packages/mine" className="mt-3 inline-block text-sm font-medium text-blue-600 hover:underline">
            Go to My Packages &rarr;
          </Link>
        </Card>
      </RoleGate>
    </div>
  )
}
