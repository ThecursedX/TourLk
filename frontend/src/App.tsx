import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './auth/authStore'
import ProtectedRoute from './auth/ProtectedRoute'
import Layout from './components/layout/Layout'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import NotFoundPage from './pages/NotFoundPage'

function RootRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  return <Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />
}

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<RootRedirect />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Layout>
  )
}

export default App
