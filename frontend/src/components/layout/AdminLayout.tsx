import { NavLink, Outlet } from 'react-router-dom'

const ADMIN_LINKS = [
  { to: '/admin/destinations', label: 'Destinations' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/packages', label: 'Package Approvals' },
  { to: '/admin/bookings', label: 'Bookings' },
  { to: '/admin/accommodations', label: 'Property Approvals' },
  { to: '/admin/vehicles', label: 'Vehicle Approvals' },
  { to: '/admin/payments', label: 'Payments' },
  { to: '/admin/tickets', label: 'Support Tickets' },
  { to: '/admin/tickets/unassigned', label: 'Unassigned Tickets' },
  { to: '/admin/reviews', label: 'Moderate Reviews' },
]

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-xl px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-cobalt-50 text-cobalt-700' : 'text-slate-700 hover:bg-slate-50'
  }`

/**
 * Shared shell for every /admin/* page. The sidebar is a true `fixed`
 * element (not sticky-in-flow) so it stays put while the matched page,
 * rendered via <Outlet />, scrolls independently beside it. `<aside>`
 * itself is only a flow placeholder — same width as the fixed nav — so the
 * content column is pushed over by the right amount without the two having
 * to coordinate any other way. Its `left` mirrors this page's own
 * `mx-auto max-w-5xl px-4` centering, so it lines up with the content
 * column's left edge at any viewport width.
 */
export default function AdminLayout() {
  return (
    <div className="flex flex-col gap-6 lg:flex-row lg:gap-8">
      <aside className="shrink-0 lg:w-56">
        <nav
          className="glass-panel flex flex-col gap-1 rounded-2xl border border-slate-200 bg-white/90 p-3 shadow-soft lg:fixed lg:top-28 lg:z-40 lg:max-h-[calc(100vh-8rem)] lg:w-56 lg:overflow-y-auto lg:left-[max(1rem,calc((100vw-64rem)/2+1rem))]"
        >
          <span className="px-3 pb-2 pt-1 font-display text-xs font-bold uppercase tracking-wide text-slate-500">
            Admin
          </span>
          {ADMIN_LINKS.map((link) => (
            <NavLink key={link.to} to={link.to} end className={navLinkClass}>
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
