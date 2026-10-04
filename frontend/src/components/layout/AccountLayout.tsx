import { NavLink, Outlet } from 'react-router-dom'
import { useAuthStore } from '../../auth/authStore'
import type { Role } from '../../types/auth'

interface SidebarLink {
  to: string
  label: string
}

const ROLE_LINKS: Partial<Record<Role, SidebarLink[]>> = {
  TOURIST: [
    { to: '/bookings/mine', label: 'My Bookings' },
    { to: '/reservations/mine', label: 'My Reservations' },
    { to: '/hires/mine', label: 'My Hires' },
    { to: '/payments/mine', label: 'My Payments' },
    { to: '/payment-methods', label: 'Saved Cards' },
    { to: '/reviews/mine', label: 'My Reviews' },
    { to: '/support/mine', label: 'My Tickets' },
  ],
  GUIDE: [
    { to: '/packages/mine', label: 'My Packages' },
    { to: '/packages/mine/bookings', label: 'Package Bookings' },
  ],
  HOTEL_PARTNER: [
    { to: '/accommodations/mine', label: 'My Properties' },
    { to: '/accommodations/owner/reservations', label: 'Reservations' },
  ],
  DRIVER: [
    { to: '/vehicles/mine', label: 'My Vehicles' },
    { to: '/vehicles/owner/hires', label: 'Hires' },
  ],
}

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-xl px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-cobalt-50 text-cobalt-700' : 'text-slate-700 hover:bg-slate-50'
  }`

/**
 * Shared shell for the signed-in "account" pages (dashboard, profile, and
 * every "my/mine" page) for Tourist, Guide, Hotel Partner and Driver — a
 * fixed left sidebar, styled the same way as AdminLayout's, collects that
 * role's "My X" links so they don't have to live in the header. Admins pass
 * straight through with no sidebar here; their management nav lives in
 * AdminLayout under /admin instead.
 */
export default function AccountLayout() {
  const user = useAuthStore((state) => state.user)
  const links = user ? ROLE_LINKS[user.role] : undefined

  if (!links || links.length === 0) {
    return <Outlet />
  }

  return (
    <div className="flex flex-col gap-6 lg:flex-row lg:gap-8">
      <aside className="shrink-0 lg:w-56">
        <nav className="glass-panel flex flex-col gap-1 rounded-2xl border border-slate-200 bg-white/90 p-3 shadow-soft lg:fixed lg:top-28 lg:z-40 lg:max-h-[calc(100vh-8rem)] lg:w-56 lg:overflow-y-auto lg:left-[max(1rem,calc((100vw-64rem)/2+1rem))]">
          <span className="px-3 pb-2 pt-1 font-display text-xs font-bold uppercase tracking-wide text-slate-500">
            My Account
          </span>
          <NavLink to="/dashboard" end className={navLinkClass}>
            Dashboard
          </NavLink>
          {links.map((link) => (
            <NavLink key={link.to} to={link.to} className={navLinkClass}>
              {link.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0 flex-1">
        <Outlet />
      </div>
    </div>
  )
}
