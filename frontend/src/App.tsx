import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './auth/authStore'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './components/layout/Layout'
import AdminLayout from './components/layout/AdminLayout'
import AccountLayout from './components/layout/AccountLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage'
import ResetPasswordPage from './pages/auth/ResetPasswordPage'
import DashboardPage from './pages/DashboardPage'
import NotFoundPage from './pages/NotFoundPage'
import PackageListPage from './pages/packages/PackageListPage'
import PackageDetailPage from './pages/packages/PackageDetailPage'
import MyPackagesPage from './pages/packages/MyPackagesPage'
import MyPackageBookingsPage from './pages/bookings/MyPackageBookingsPage'
import CreatePackagePage from './pages/packages/CreatePackagePage'
import EditPackagePage from './pages/packages/EditPackagePage'
import AdminPackagesPage from './pages/packages/AdminPackagesPage'
import MyBookingsPage from './pages/bookings/MyBookingsPage'
import BookingDetailPage from './pages/bookings/BookingDetailPage'
import AdminBookingsPage from './pages/bookings/AdminBookingsPage'
import DestinationListPage from './pages/destinations/DestinationListPage'
import DestinationDetailPage from './pages/destinations/DestinationDetailPage'
import AdminDestinationsPage from './pages/destinations/AdminDestinationsPage'
import AccommodationListPage from './pages/accommodations/AccommodationListPage'
import AccommodationDetailPage from './pages/accommodations/AccommodationDetailPage'
import MyAccommodationsPage from './pages/accommodations/MyAccommodationsPage'
import CreateAccommodationPage from './pages/accommodations/CreateAccommodationPage'
import EditAccommodationPage from './pages/accommodations/EditAccommodationPage'
import AdminAccommodationApprovalsPage from './pages/accommodations/AdminAccommodationApprovalsPage'
import MyReservationsPage from './pages/reservations/MyReservationsPage'
import OwnerReservationsPage from './pages/reservations/OwnerReservationsPage'
import VehicleListPage from './pages/vehicles/VehicleListPage'
import VehicleDetailPage from './pages/vehicles/VehicleDetailPage'
import MyVehiclesPage from './pages/vehicles/MyVehiclesPage'
import CreateVehiclePage from './pages/vehicles/CreateVehiclePage'
import EditVehiclePage from './pages/vehicles/EditVehiclePage'
import AdminVehicleApprovalsPage from './pages/vehicles/AdminVehicleApprovalsPage'
import MyHiresPage from './pages/vehicles/MyHiresPage'
import OwnerHiresPage from './pages/vehicles/OwnerHiresPage'
import CheckoutPage from './pages/payments/CheckoutPage'
import PaymentMethodsPage from './pages/payments/PaymentMethodsPage'
import MyPaymentsPage from './pages/payments/MyPaymentsPage'
import InvoicePage from './pages/payments/InvoicePage'
import AdminPaymentsPage from './pages/payments/AdminPaymentsPage'
import MyReviewsPage from './pages/reviews/MyReviewsPage'
import MyTicketsPage from './pages/support/MyTicketsPage'
import NewTicketPage from './pages/support/NewTicketPage'
import TicketDetailPage from './pages/support/TicketDetailPage'
import AdminTicketsPage from './pages/support/AdminTicketsPage'
import AdminUnassignedTicketsPage from './pages/support/AdminUnassignedTicketsPage'
import ProfilePage from './pages/profile/ProfilePage'
import NotificationsPage from './pages/notifications/NotificationsPage'
import AdminUsersPage from './pages/users/AdminUsersPage'
import AdminLicenceVerificationsPage from './pages/users/AdminLicenceVerificationsPage'
import AdminReviewsPage from './pages/reviews/AdminReviewsPage'

function RootRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }
  return <HomePage />
}

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<RootRedirect />} />
        {/* Always shows the public homepage, even for signed-in users — see
            the Navbar logo (signed-in users) and the admin Homepage menu. */}
        <Route path="/home" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password/:token" element={<ResetPasswordPage />} />

        <Route path="/destinations" element={<DestinationListPage />} />
        <Route path="/destinations/:id" element={<DestinationDetailPage />} />
        <Route path="/packages" element={<PackageListPage />} />
        <Route path="/packages/:id" element={<PackageDetailPage />} />
        <Route path="/accommodations" element={<AccommodationListPage />} />
        <Route path="/accommodations/:id" element={<AccommodationDetailPage />} />
        <Route path="/vehicles" element={<VehicleListPage />} />
        <Route path="/vehicles/:id" element={<VehicleDetailPage />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AccountLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/bookings/mine" element={<MyBookingsPage />} />
            <Route path="/bookings/:id" element={<BookingDetailPage />} />
            <Route path="/vehicles/mine" element={<MyVehiclesPage />} />
            <Route path="/vehicles/new" element={<CreateVehiclePage />} />
            <Route path="/vehicles/:id/edit" element={<EditVehiclePage />} />
            <Route path="/vehicles/owner/hires" element={<OwnerHiresPage />} />
            <Route path="/hires/mine" element={<MyHiresPage />} />
            <Route path="/checkout/:payableType/:payableId" element={<CheckoutPage />} />
            <Route path="/payments/mine" element={<MyPaymentsPage />} />
            <Route path="/payment-methods" element={<PaymentMethodsPage />} />
            <Route path="/payments/:id/invoice" element={<InvoicePage />} />
            <Route path="/reviews/mine" element={<MyReviewsPage />} />
            <Route path="/support/mine" element={<MyTicketsPage />} />
            <Route path="/support/new" element={<NewTicketPage />} />
            <Route path="/support/:id" element={<TicketDetailPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute roles={['ADMIN', 'HOTEL_PARTNER']} />}>
          <Route element={<AccountLayout />}>
            <Route path="/accommodations/mine" element={<MyAccommodationsPage />} />
            <Route path="/accommodations/new" element={<CreateAccommodationPage />} />
            <Route path="/accommodations/:id/edit" element={<EditAccommodationPage />} />
            <Route path="/accommodations/owner/reservations" element={<OwnerReservationsPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute roles={['TOURIST']} />}>
          <Route element={<AccountLayout />}>
            <Route path="/reservations/mine" element={<MyReservationsPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute roles={['ADMIN', 'GUIDE']} />}>
          <Route element={<AccountLayout />}>
            <Route path="/packages/mine" element={<MyPackagesPage />} />
            <Route path="/packages/mine/bookings" element={<MyPackageBookingsPage />} />
            <Route path="/packages/new" element={<CreatePackagePage />} />
            <Route path="/packages/:id/edit" element={<EditPackagePage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute roles={['ADMIN']} />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Navigate to="/admin/destinations" replace />} />
            <Route path="destinations" element={<AdminDestinationsPage />} />
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="packages" element={<AdminPackagesPage />} />
            <Route path="bookings" element={<AdminBookingsPage />} />
            <Route path="accommodations" element={<AdminAccommodationApprovalsPage />} />
            <Route path="vehicles" element={<AdminVehicleApprovalsPage />} />
            <Route path="verifications" element={<AdminLicenceVerificationsPage />} />
            <Route path="payments" element={<AdminPaymentsPage />} />
            <Route path="tickets" element={<AdminTicketsPage />} />
            <Route path="tickets/unassigned" element={<AdminUnassignedTicketsPage />} />
            <Route path="reviews" element={<AdminReviewsPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Layout>
  )
}

export default App
