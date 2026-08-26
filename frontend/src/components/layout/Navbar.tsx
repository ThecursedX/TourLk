import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../auth/authStore'
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
          {isAuthenticated && user ? (
            <>
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
