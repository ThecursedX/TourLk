import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
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

interface LocationState {
  from?: { pathname: string }
}

export default function LoginPage() {
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()
  const location = useLocation()

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
      const state = location.state as LocationState | null
      navigate(state?.from?.pathname ?? '/dashboard', { replace: true })
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
    <div className="flex justify-center">
      <Card className="w-full max-w-sm">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Log in</h1>
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
