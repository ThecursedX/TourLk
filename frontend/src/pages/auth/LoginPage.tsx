import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { useAuthStore } from '../../auth/authStore'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'

interface FormValues {
  email: string
  password: string
}

export default function LoginPage() {
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()

  const [values, setValues] = useState<FormValues>({ email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.email.trim()) {
      errors.email = 'Email is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) {
      errors.email = 'Email must be valid'
    }
    if (!values.password) {
      errors.password = 'Password is required'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await login(values)
      // Always route by the freshly logged-in user's role — never by whatever
      // page happened to be open before logging out (that page may belong to
      // a different role entirely).
      const loggedInUser = useAuthStore.getState().user
      navigate(loggedInUser?.role === 'TOURIST' ? '/' : '/dashboard', { replace: true })
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setFormError(err.response.data.message)
        setFieldErrors(err.response.data.fieldErrors ?? {})
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col items-center gap-6 py-8">
      <div className="flex items-center gap-2 font-display text-lg font-extrabold text-slate-900">
        <svg width="30" height="30" viewBox="0 0 34 34" fill="none" aria-hidden="true">
          <circle cx="17" cy="17" r="17" fill="#0F6259" />
          <path
            d="M6 21C9 17 12 23 15 19C18 15 21 21 24 17C26.5 13.7 28 15 28 15"
            stroke="#F6EEDC"
            strokeWidth="2"
            strokeLinecap="round"
            fill="none"
          />
        </svg>
        TourLK
      </div>
      <Card className="w-full max-w-sm">
        <h1 className="mb-6 font-display text-xl font-bold text-slate-900">Welcome back</h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <Input
            id="email"
            label="Email"
            type="email"
            value={values.email}
            onChange={(e) => setValues((v) => ({ ...v, email: e.target.value }))}
            error={fieldErrors.email}
          />
          <Input
            id="password"
            label="Password"
            type="password"
            value={values.password}
            onChange={(e) => setValues((v) => ({ ...v, password: e.target.value }))}
            error={fieldErrors.password}
          />
          <Link to="/forgot-password" className="-mt-2 self-end text-sm text-blue-600 hover:underline">
            Forgot password?
          </Link>
          {formError && <p className="text-sm text-red-600">{formError}</p>}
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Logging in...' : 'Log in'}
          </Button>
        </form>
        <p className="mt-4 text-sm text-slate-600">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="font-medium text-blue-600 hover:underline">
            Register
          </Link>
        </p>
      </Card>
    </div>
  )
}
