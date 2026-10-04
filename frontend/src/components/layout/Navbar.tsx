import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../auth/authStore'
import Button from '../ui/Button'
import NotificationBell from './NotificationBell'

const BROWSE_LINKS = [
  { to: '/destinations', label: 'Destinations' },
  { to: '/packages', label: 'Packages' },
  { to: '/accommodations', label: 'Stays' },
  { to: '/vehicles', label: 'Transport' },
]

/**
 * Admin-only "Homepage" item: the label links to /home, the chevron toggles
 * the browse list. Opens on hover (desktop) and click/tap; closes on outside
 * click, Escape, route change or choosing a link.
 */
function HomepageDropdown() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const location = useLocation()

  useEffect(() => {
    setOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent | TouchEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('touchstart', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('touchstart', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div
      ref={ref}
      className="relative flex items-center"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <Link to="/home" className={navLink} onClick={() => setOpen(false)}>
        Homepage
      </Link>
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Browse the site"
        onClick={() => setOpen((prev) => !prev)}
        className="ml-1 rounded-full p-1 text-slate-600 hover:bg-slate-100 hover:text-blue-600"
      >
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true"
          className={`transition-transform ${open ? 'rotate-180' : ''}`}>
          <path d="M2 4l4 4 4-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && (
        // pt-2 (not margin) keeps the hover area contiguous between trigger and panel.
        <div className="absolute right-0 top-full z-50 pt-2">
          <div
            role="menu"
            className="flex min-w-44 flex-col gap-1 rounded-2xl border border-slate-200 bg-white p-2 shadow-soft"
          >
            {BROWSE_LINKS.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                role="menuitem"
                onClick={() => setOpen(false)}
                className={({ isActive }) =>
                  `rounded-xl px-3 py-2 text-sm font-medium hover:bg-slate-50 ${
                    isActive ? 'bg-cobalt-50 text-cobalt-700' : 'text-slate-700'
                  }`
                }
              >
                {link.label}
              </NavLink>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

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
  const isAdmin = isAuthenticated && user?.role === 'ADMIN'

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="fixed inset-x-0 top-4 z-50 px-4">
      <header className="glass-panel mx-auto flex max-w-5xl items-center justify-between gap-4 rounded-full border border-white/50 bg-white/65 px-5 py-2.5 shadow-soft">
        <Link to={isAdmin ? '/dashboard' : isAuthenticated ? '/home' : '/'} className="flex shrink-0 items-center gap-2 font-display text-base font-extrabold text-slate-900">
          <LogoMark />
          TourLK
        </Link>
        {!isAdmin && (
          <nav className="flex flex-wrap items-center gap-4">
            {BROWSE_LINKS.map((link) => (
              <Link key={link.to} to={link.to} className={navLink}>
                {link.label}
              </Link>
            ))}
          </nav>
        )}
        <div className="flex shrink-0 items-center gap-3">
          {isAuthenticated && user ? (
            <>
              {isAdmin ? (
                <>
                  <HomepageDropdown />
                  <NavLink
                    to="/admin"
                    className={({ isActive }) =>
                      `${navLink} rounded-full px-3 py-1 ${isActive ? 'bg-blue-50 text-blue-600' : ''}`
                    }
                  >
                    Admin
                  </NavLink>
                </>
              ) : (
                <Link to="/dashboard" className={navLink}>
                  Dashboard
                </Link>
              )}
              <NotificationBell />
              <Link
                to="/profile"
                title="Profile"
                className="flex items-center gap-2 rounded-full bg-slate-100 py-1 pl-1 pr-3 text-sm text-slate-700 transition-colors hover:bg-blue-50 hover:text-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400 active:bg-blue-100"
              >
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-[11px] font-bold text-white">
                  {user.name?.slice(0, 1).toUpperCase()}
                </span>
                <span className="hidden sm:inline">{user.name}</span>
                <span className="hidden text-slate-400 sm:inline">({user.role})</span>
              </Link>
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
