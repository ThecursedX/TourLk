import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../auth/authStore'
import Button from '../ui/Button'
import NotificationBell from './NotificationBell'

const navLink = 'text-sm font-medium text-slate-700 hover:text-blue-600 transition-colors'

function LogoMark() {
  return (
    <svg width="28" height="28" viewBox="0 0 34 34" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <circle cx="17" cy="17" r="17" fill="#0F6259" />
      <path
        d="M6 21C9 17 12 23 15 19C18 15 21 21 24 17C26.5 13.7 28 15 28 15"
        stroke="#F6EEDC"
        strokeWidth="2"
        strokeLinecap="round"
        fill="none"
      />
      <path d="M17 15C17 15 15.5 9 10 9" stroke="#F6EEDC" strokeWidth="2" strokeLinecap="round" fill="none" />
    </svg>
  )
}

/**
 * A floating, frosted-glass pill — detached from the top edge, rounded all
 * the way round. Branding, public browse links, and the signed-in user's
 * profile/logout live here; role-specific navigation lives in the Profile
 * page and the admin sidebar instead, so this stays short no matter the role.
 */
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
    <div className="fixed inset-x-0 top-4 z-50 px-4">
      <header className="glass-panel mx-auto flex max-w-5xl items-center justify-between gap-4 rounded-full border border-white/50 bg-white/65 px-5 py-2.5 shadow-soft">
        <Link to="/" className="flex shrink-0 items-center gap-2 font-display text-base font-extrabold text-slate-900">
          <LogoMark />
          TourLK
        </Link>
        <nav className="flex flex-wrap items-center gap-4">
          <Link to="/destinations" className={navLink}>
            Destinations
          </Link>
          <Link to="/packages" className={navLink}>
            Packages
          </Link>
          <Link to="/accommodations" className={navLink}>
            Stays
          </Link>
          <Link to="/vehicles" className={navLink}>
            Transport
          </Link>
        </nav>
        <div className="flex shrink-0 items-center gap-3">
          {isAuthenticated && user ? (
            <>
              {user.role !== 'ADMIN' && (
                <Link to="/home" className={navLink}>
                  Homepage
                </Link>
              )}
              <Link to="/profile" className={navLink}>
                Profile
              </Link>
              <NotificationBell />
              <span className="flex items-center gap-2 rounded-full bg-slate-100 py-1 pl-1 pr-3 text-sm text-slate-700">
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-[11px] font-bold text-white">
                  {user.name?.slice(0, 1).toUpperCase()}
                </span>
                <span className="hidden sm:inline">{user.name}</span>
                <span className="hidden text-slate-400 sm:inline">({user.role})</span>
              </span>
              <Button variant="secondary" onClick={handleLogout}>
                Logout
              </Button>
            </>
          ) : (
            <>
              <Link to="/login" className={navLink}>
                Login
              </Link>
              <Link to="/register">
                <Button>Register</Button>
              </Link>
            </>
          )}
        </div>
      </header>
    </div>
  )
}
