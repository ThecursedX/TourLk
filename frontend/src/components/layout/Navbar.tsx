import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../auth/authStore'
import RoleGate from '../../auth/RoleGate'
import Button from '../ui/Button'

export default function Navbar() {
  const user = useAuthStore((state) => state.user)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link to="/" className="text-lg font-semibold text-slate-900">
          TourLK
        </Link>
        <nav className="flex items-center gap-4">
          <Link to="/destinations" className="text-sm font-medium text-slate-700 hover:text-blue-600">
            Destinations
          </Link>
          <Link to="/packages" className="text-sm font-medium text-slate-700 hover:text-blue-600">
            Packages
          </Link>
          <Link to="/accommodations" className="text-sm font-medium text-slate-700 hover:text-blue-600">
            Stays
          </Link>
          <Link to="/vehicles" className="text-sm font-medium text-slate-700 hover:text-blue-600">
            Transport
          </Link>
          {isAuthenticated && user ? (
            <>
              <Link to="/support/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                Support
              </Link>
              <RoleGate allowed={['TOURIST']}>
                <Link to="/bookings/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  My Bookings
                </Link>
                <Link to="/reservations/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  My Reservations
                </Link>
                <Link to="/hires/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  My Hires
                </Link>
                <Link to="/payments/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  My Payments
                </Link>
                <Link to="/reviews/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  My Reviews
                </Link>
              </RoleGate>
              <RoleGate allowed={['HOTEL_PARTNER']}>
                <Link to="/accommodations/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  My Properties
                </Link>
                <Link
                  to="/accommodations/owner/reservations"
                  className="text-sm font-medium text-slate-700 hover:text-blue-600"
                >
                  Reservations
                </Link>
              </RoleGate>
              <RoleGate allowed={['DRIVER']}>
                <Link to="/vehicles/mine" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  My Vehicles
                </Link>
                <Link to="/vehicles/owner/hires" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  Hires
                </Link>
              </RoleGate>
              <RoleGate allowed={['ADMIN']}>
                <Link
                  to="/admin/destinations"
                  className="text-sm font-medium text-slate-700 hover:text-blue-600"
                >
                  Manage Destinations
                </Link>
                <Link to="/admin/packages" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  Admin Approvals
                </Link>
                <Link to="/admin/bookings" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  Manage Bookings
                </Link>
                <Link
                  to="/admin/accommodations"
                  className="text-sm font-medium text-slate-700 hover:text-blue-600"
                >
                  Property Approvals
                </Link>
                <Link to="/admin/vehicles" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  Vehicle Approvals
                </Link>
                <Link to="/admin/payments" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  Payments
                </Link>
                <Link to="/admin/tickets" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                  Support Tickets
                </Link>
                <Link
                  to="/admin/tickets/unassigned"
                  className="text-sm font-medium text-slate-700 hover:text-blue-600"
                >
                  Unassigned Tickets
                </Link>
              </RoleGate>
              <span className="text-sm text-slate-600">
                {user.name} <span className="text-slate-400">({user.role})</span>
              </span>
              <Button variant="secondary" onClick={handleLogout}>
                Logout
              </Button>
            </>
          ) : (
            <>
              <Link to="/login" className="text-sm font-medium text-slate-700 hover:text-blue-600">
                Login
              </Link>
              <Link to="/register">
                <Button>Register</Button>
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
