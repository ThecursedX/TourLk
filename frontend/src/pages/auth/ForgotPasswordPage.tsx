import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { forgotPasswordRequest } from '../../api/authApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import type { ErrorResponse } from '../../types/auth'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [fieldError, setFieldError] = useState<string | undefined>(undefined)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    setFieldError(undefined)

    if (!email.trim()) {
      setFieldError('Email is required')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setFieldError('Email must be valid')
      return
    }

    setSubmitting(true)
    try {
      await forgotPasswordRequest({ email })
      setSent(true)
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setFormError(err.response.data.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col items-center gap-6 py-8">
      <Card className="w-full max-w-sm">
        <h1 className="mb-6 font-display text-xl font-bold text-slate-900">Forgot your password?</h1>
        {sent ? (
          <p className="text-sm text-slate-600">
            If an account exists for that email, we&apos;ve sent a link to reset your password.
          </p>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
            <p className="text-sm text-slate-600">
              Enter the email address for your account and we&apos;ll send you a link to reset your password.
            </p>
            <Input
              id="email"
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={fieldError}
            />
            {formError && <p className="text-sm text-red-600">{formError}</p>}
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Sending...' : 'Send reset link'}
            </Button>
          </form>
        )}
        <p className="mt-4 text-sm text-slate-600">
          <Link to="/login" className="font-medium text-blue-600 hover:underline">
            Back to login
          </Link>
        </p>
      </Card>
    </div>
  )
}
