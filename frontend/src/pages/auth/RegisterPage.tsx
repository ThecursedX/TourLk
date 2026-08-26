import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { useAuthStore } from '../../auth/authStore'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import { ROLES } from '../../types/auth'
import type { ErrorResponse, Role } from '../../types/auth'

interface FormValues {
  name: string
  email: string
  password: string
  phone: string
  role: Role
}

const initialValues: FormValues = {
  name: '',
  email: '',
  password: '',
  phone: '',
  role: 'TOURIST',
}

export default function RegisterPage() {
  const register = useAuthStore((state) => state.register)
  const navigate = useNavigate()

  const [values, setValues] = useState<FormValues>(initialValues)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!values.name.trim()) {
      errors.name = 'Name is required'
    } else if (values.name.length > 150) {
      errors.name = 'Name must be at most 150 characters'
    }
    if (!values.email.trim()) {
      errors.email = 'Email is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) {
      errors.email = 'Email must be valid'
    }
    if (!values.password) {
      errors.password = 'Password is required'
    } else if (values.password.length < 8) {
      errors.password = 'Password must be at least 8 characters'
    }
    if (!values.role) {
      errors.role = 'Role is required'
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
      const { phone, ...rest } = values
      await register({ ...rest, phone: phone.trim() || undefined })
      navigate('/dashboard', { replace: true })
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
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Create an account</h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <Input
            id="name"
            label="Name"
            value={values.name}
            onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
            error={fieldErrors.name}
          />
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
          <Input
            id="phone"
            label="Phone (optional)"
            value={values.phone}
            onChange={(e) => setValues((v) => ({ ...v, phone: e.target.value }))}
            error={fieldErrors.phone}
          />
          <Select
            id="role"
            label="Role"
            value={values.role}
            onChange={(e) => setValues((v) => ({ ...v, role: e.target.value as Role }))}
            error={fieldErrors.role}
          >
            {ROLES.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </Select>
          {formError && <p className="text-sm text-red-600">{formError}</p>}
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Creating account...' : 'Register'}
          </Button>
        </form>
        <p className="mt-4 text-sm text-slate-600">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-blue-600 hover:underline">
            Log in
          </Link>
        </p>
      </Card>
    </div>
  )
}
