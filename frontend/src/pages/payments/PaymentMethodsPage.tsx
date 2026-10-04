import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import {
  createSetupIntent,
  deletePaymentMethod,
  getMyPaymentMethods,
  savePaymentMethod,
  setDefaultPaymentMethod,
} from '../../api/paymentMethodApi'
import stripePromise from '../../lib/stripe'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import type { ErrorResponse } from '../../types/auth'
import type { PaymentMethodResponseDto } from '../../types/paymentMethod'

function AddCardForm({ onSaved }: { onSaved: (pm: PaymentMethodResponseDto) => void }) {
  const stripe = useStripe()
  const elements = useElements()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!stripe || !elements) return

    setSubmitting(true)
    setError(null)

    const { error: confirmError, setupIntent } = await stripe.confirmSetup({
      elements,
      confirmParams: { return_url: `${window.location.origin}/payment-methods` },
      redirect: 'if_required',
    })

    if (confirmError) {
      setError(confirmError.message ?? 'Could not save this card. Please try again.')
      setSubmitting(false)
      return
    }

    if (!setupIntent || typeof setupIntent.payment_method !== 'string') {
      setError('Could not save this card. Please try again.')
      setSubmitting(false)
      return
    }

    try {
      const saved = await savePaymentMethod({ paymentMethodId: setupIntent.payment_method })
      onSaved(saved)
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setError(err.response.data.message)
      } else {
        setError('Could not save this card. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <PaymentElement />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <div>
        <Button type="submit" disabled={!stripe || submitting}>
          {submitting ? 'Saving...' : 'Save Card'}
        </Button>
      </div>
    </form>
  )
}

export default function PaymentMethodsPage() {
  const [methods, setMethods] = useState<PaymentMethodResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [clientSecret, setClientSecret] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    getMyPaymentMethods()
      .then(setMethods)
      .catch(() => setLoadError('Could not load your saved cards. Please try again later.'))
      .finally(() => setLoading(false))
  }, [])

  const startAddingCard = async () => {
    setActionError(null)
    try {
      const intent = await createSetupIntent()
      setClientSecret(intent.clientSecret)
      setAdding(true)
    } catch {
      setActionError('Could not start adding a card. Please try again later.')
    }
  }

  const handleSaved = (pm: PaymentMethodResponseDto) => {
    setMethods((prev) => [pm, ...prev])
    setAdding(false)
    setClientSecret(null)
  }

  const handleSetDefault = async (id: number) => {
    setActionError(null)
    try {
      const updated = await setDefaultPaymentMethod(id)
      setMethods((prev) => prev.map((m) => (m.id === updated.id ? updated : { ...m, defaultCard: false })))
    } catch {
      setActionError('Could not update your default card. Please try again later.')
    }
  }

  const handleDelete = async (id: number) => {
    setActionError(null)
    try {
      await deletePaymentMethod(id)
      setMethods((prev) => prev.filter((m) => m.id !== id))
    } catch {
      setActionError('Could not remove this card. Please try again later.')
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Saved Cards</h1>
        <p className="mt-1 text-slate-600">Manage the cards saved for faster checkout.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {loadError && <p className="text-red-600">{loadError}</p>}

      {!loading && !loadError && (
        <Card className="max-w-lg">
          {actionError && <p className="mb-4 text-sm text-red-600">{actionError}</p>}

          {methods.length === 0 && !adding && (
            <p className="mb-4 text-sm text-slate-600">You have no saved cards yet.</p>
          )}

          {methods.length > 0 && (
            <ul className="mb-4 flex flex-col gap-3">
              {methods.map((m) => (
                <li
                  key={m.id}
                  className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 p-3"
                >
                  <span className="text-sm text-slate-700">
                    {m.brand.toUpperCase()} &bull;&bull;&bull;&bull; {m.last4} &middot; exp {m.expMonth}/{m.expYear}
                    {m.defaultCard && (
                      <span className="ml-2 rounded-full bg-cobalt-50 px-2 py-0.5 text-xs font-semibold text-cobalt-700">
                        Default
                      </span>
                    )}
                  </span>
                  <span className="flex gap-2">
                    {!m.defaultCard && (
                      <Button type="button" variant="secondary" onClick={() => handleSetDefault(m.id)}>
                        Make Default
                      </Button>
                    )}
                    <Button type="button" variant="ghost" onClick={() => handleDelete(m.id)}>
                      Remove
                    </Button>
                  </span>
                </li>
              ))}
            </ul>
          )}

          {adding && clientSecret ? (
            <Elements stripe={stripePromise} options={{ clientSecret }}>
              <AddCardForm onSaved={handleSaved} />
            </Elements>
          ) : (
            <Button type="button" onClick={startAddingCard}>
              Add a Card
            </Button>
          )}
        </Card>
      )}
    </div>
  )
}
