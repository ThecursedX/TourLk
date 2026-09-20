import { Link } from 'react-router-dom'
import { useAuthStore } from '../auth/authStore'
import RoleGate from '../auth/RoleGate'

interface ModuleTileProps {
  to: string
  title: string
  description: string
  tone?: 'ocean' | 'sand' | 'cobalt' | 'dark'
}

const toneClasses: Record<NonNullable<ModuleTileProps['tone']>, string> = {
  ocean: 'bg-blue-50 text-blue-700',
  sand: 'bg-amber-50 text-amber-700',
  cobalt: 'bg-cobalt-50 text-cobalt-600',
  dark: 'bg-white/10 text-slate-50',
}

function ModuleTile({ to, title, description, tone = 'ocean' }: ModuleTileProps) {
  const isDark = tone === 'dark'
  return (
    <Link
      to={to}
      className={`flex items-start gap-4 rounded-2xl border p-5 shadow-soft transition-shadow hover:shadow-md ${
        isDark ? 'border-blue-700 bg-blue-700' : 'border-slate-200 bg-white'
      }`}
    >
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl font-display text-sm font-bold ${toneClasses[tone]}`}
      >
        {title.trim().slice(0, 1)}
      </span>
      <span className="flex flex-col gap-1">
        <span className={`font-display text-sm font-bold ${isDark ? 'text-slate-50' : 'text-slate-900'}`}>
          {title}
        </span>
        <span className={`text-xs ${isDark ? 'text-blue-100' : 'text-slate-600'}`}>{description}</span>
      </span>
    </Link>
  )
}

export default function DashboardPage() {
  const user = useAuthStore((state) => state.user)

  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-col gap-2">
        <h1 className="font-display text-2xl font-extrabold text-slate-900">Welcome back, {user?.name}</h1>
        <p className="text-slate-600">
          You&rsquo;re signed in as <span className="font-medium text-blue-700">{user?.role}</span>. Here&rsquo;s
          where each module lives.
        </p>
      </div>

      <RoleGate allowed={['TOURIST']}>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ModuleTile to="/bookings/mine" title="My Bookings" description="Track your tour package bookings" />
          <ModuleTile
            to="/reservations/mine"
            title="My Reservations"
            description="Manage your room stays"
            tone="sand"
          />
          <ModuleTile to="/hires/mine" title="My Hires" description="Vehicles &amp; drivers you've booked" tone="cobalt" />
          <ModuleTile to="/payments/mine" title="My Payments" description="Invoices &amp; payment history" />
          <ModuleTile to="/reviews/mine" title="My Reviews" description="Feedback you've shared" tone="sand" />
          <ModuleTile to="/support/mine" title="Support" description="Raise or track a ticket" tone="dark" />
        </div>
      </RoleGate>

      <RoleGate allowed={['ADMIN', 'GUIDE']}>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ModuleTile to="/packages/mine" title="My Packages" description="Create and manage the tour packages you offer" />
        </div>
      </RoleGate>

      <RoleGate allowed={['HOTEL_PARTNER']}>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ModuleTile to="/accommodations/mine" title="My Properties" description="Manage your listed accommodations" />
          <ModuleTile
            to="/accommodations/owner/reservations"
            title="Reservations"
            description="Review incoming room reservations"
            tone="sand"
          />
        </div>
      </RoleGate>

      <RoleGate allowed={['DRIVER']}>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ModuleTile to="/vehicles/mine" title="My Vehicles" description="Manage your registered vehicles" />
          <ModuleTile to="/vehicles/owner/hires" title="Hires" description="Review incoming hire requests" tone="sand" />
        </div>
      </RoleGate>

      <RoleGate allowed={['ADMIN']}>
        <div className="flex flex-col gap-3">
          <h2 className="font-display text-lg font-bold text-slate-900">Admin</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <ModuleTile to="/admin/destinations" title="Destinations" description="Manage destination listings" />
            <ModuleTile to="/admin/users" title="Users" description="Manage platform users" tone="sand" />
            <ModuleTile to="/admin/packages" title="Package Approvals" description="Review pending tour packages" />
            <ModuleTile to="/admin/bookings" title="Bookings" description="Oversee all bookings" tone="sand" />
            <ModuleTile
              to="/admin/accommodations"
              title="Property Approvals"
              description="Review pending accommodations"
            />
            <ModuleTile to="/admin/vehicles" title="Vehicle Approvals" description="Review pending vehicles" tone="sand" />
            <ModuleTile to="/admin/payments" title="Payments" description="Oversee platform payments" />
            <ModuleTile to="/admin/tickets" title="Support Tickets" description="Manage support tickets" tone="sand" />
            <ModuleTile
              to="/admin/tickets/unassigned"
              title="Unassigned Tickets"
              description="Assign incoming tickets"
              tone="cobalt"
            />
            <ModuleTile to="/admin/reviews" title="Moderate Reviews" description="Review flagged reviews" tone="sand" />
          </div>
        </div>
      </RoleGate>
    </div>
  )
}
