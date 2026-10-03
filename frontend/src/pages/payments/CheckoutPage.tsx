import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { getBookingById } from '../../api/bookingApi'
import { getReservationById } from '../../api/roomReservationApi'
import { getHireById } from '../../api/vehicleHireApi'
import { createPaymentIntent } from '../../api/paymentApi'
import { getMyPaymentMethods } from '../../api/paymentMethodApi'
import stripePromise from '../../lib/stripe'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import type { ErrorResponse } from '../../types/auth'
import type { PayableType } from '../../types/payment'
import type { PaymentMethodResponseDto } from '../../types/paymentMethod'

interface PayableSummary {
  title: string
  amount: number
}

function PaymentForm({ onSuccess }: { onSuccess: () => void }) {
  const stripe = useStripe()
  const elements = useElements()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!stripe || !elements) return

    setSubmitting(true)
    setError(null)

    const { error: confirmError } = await stripe.confirmPayment({
      elements,
      confirmParams: {
        return_url: `${window.location.origin}/payments/mine`,
      },
      redirect: 'if_required',
    })

    if (confirmError) {
      setError(confirmError.message ?? 'Payment could not be completed. Please try again.')
      setSubmitting(false)
      return
    }

    onSuccess()
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <PaymentElement />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <Button type="submit" disabled={!stripe || submitting}>
        {submitting ? 'Processing...' : 'Pay Now'}
      </Button>
    </form>
  )
}

export default function CheckoutPage() {
  const { payableType, payableId } = useParams<{ payableType: string; payableId: string }>()
  const navigate = useNavigate()

  const [summary, setSummary] = useState<PayableSummary | null>(null)
  // One new-card PaymentIntent per checkout, created only once the Payment Element is actually shown.
  const [clientSecret, setClientSecret] = useState<string | null>(null)
  const [showNewCard, setShowNewCard] = useState(false)
  const [intentLoading, setIntentLoading] = useState(false)
  const [intentError, setIntentError] = useState<string | null>(null)
  const intentRequested = useRef(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [succeeded, setSucceeded] = useState(false)
  const [savedMethods, setSavedMethods] = useState<PaymentMethodResponseDto[]>([])
  const [selectedSavedId, setSelectedSavedId] = useState<number | null>(null)
  const [payingWithSaved, setPayingWithSaved] = useState(false)
  const [savedCardError, setSavedCardError] = useState<string | null>(null)

  const normalizedType: PayableType =
    payableType === 'reservation'
      ? 'ROOM_RESERVATION'
      : payableType === 'vehicle_hire'
        ? 'VEHICLE_HIRE'
        : 'BOOKING'

  const noun =
    normalizedType === 'ROOM_RESERVATION' ? 'reservation' : normalizedType === 'VEHICLE_HIRE' ? 'hire' : 'booking'
  const payLaterPath =
    normalizedType === 'ROOM_RESERVATION'
      ? '/reservations/mine'
      : normalizedType === 'VEHICLE_HIRE'
        ? '/hires/mine'
        : '/bookings/mine'

  useEffect(() => {
    if (!payableId) return

    const id = Number(payableId)
    setLoading(true)
    setError(null)
    setClientSecret(null)
    setShowNewCard(false)
    setIntentError(null)
    intentRequested.current = false

    const loadSummary = async () => {
      try {
        let title: string
        let amount: number

        if (normalizedType === 'BOOKING') {
          const booking = await getBookingById(id)
          title = booking.tourPackage.title
          amount = booking.totalPrice
        } else if (normalizedType === 'ROOM_RESERVATION') {
          const reservation = await getReservationById(id)
          const nights =
            (new Date(reservation.checkOutDate).getTime() - new Date(reservation.checkInDate).getTime()) /
            (1000 * 60 * 60 * 24)
          title = `${reservation.room.accommodationName} — ${reservation.room.roomType}`
          amount = reservation.room.pricePerNight * nights * reservation.numberOfRooms
        } else {
          const hire = await getHireById(id)
          title = `${hire.vehicle.make} ${hire.vehicle.model} (${hire.vehicle.registrationNumber})`
          amount = hire.totalPrice
        }

        setSummary({ title, amount })

        const methods = await getMyPaymentMethods().catch(() => [])
        setSavedMethods(methods)
        setSelectedSavedId(methods.find((m) => m.defaultCard)?.id ?? methods[0]?.id ?? null)
        // Without a saved card the Payment Element is the only way to pay, so show it straight away.
        setShowNewCard(methods.length === 0)
      } catch (err) {
        if (isAxiosError<ErrorResponse>(err) && err.response) {
          setError(err.response.data.message)
        } else {
          setError('Could not start checkout. Please try again later.')
        }
      } finally {
        setLoading(false)
      }
    }

    loadSummary()
  }, [payableId, normalizedType])

  // Create the new-card intent only when the Payment Element is shown — and only once per checkout.
  useEffect(() => {
    if (!showNewCard || !summary || !payableId || intentRequested.current) return
    intentRequested.current = true
    setIntentLoading(true)
    setIntentError(null)
    createPaymentIntent({ payableType: normalizedType, payableId: Number(payableId), amount: summary.amount })
      .then((intent) => setClientSecret(intent.clientSecret))
      .catch((err) => {
        intentRequested.current = false
        setIntentError(
          isAxiosError<ErrorResponse>(err) && err.response
            ? err.response.data.message
            : 'Could not start checkout. Please try again later.',
        )
      })
      .finally(() => setIntentLoading(false))
  }, [showNewCard, summary, payableId, normalizedType])

  const handlePaySaved = async () => {
    if (!selectedSavedId || !summary || !payableId) return
    const stripe = await stripePromise
    if (!stripe) return

    setPayingWithSaved(true)
    setSavedCardError(null)

    try {
      const intent = await createPaymentIntent({
        payableType: normalizedType,
        payableId: Number(payableId),
        amount: summary.amount,
        savedPaymentMethodId: selectedSavedId,
      })

      const { paymentIntent, error: retrieveError } = await stripe.retrievePaymentIntent(intent.clientSecret)
      if (retrieveError) {
        setSavedCardError(retrieveError.message ?? 'Payment could not be completed. Please try again.')
        return
      }

      if (paymentIntent?.status === 'requires_action') {
        const { error: actionError } = await stripe.handleNextAction({ clientSecret: intent.clientSecret })
        if (actionError) {
          setSavedCardError(actionError.message ?? 'Payment could not be completed. Please try again.')
          return
        }
      } else if (paymentIntent?.status !== 'succeeded' && paymentIntent?.status !== 'processing') {
        setSavedCardError('Payment could not be completed. Please try again.')
        return
      }

      setSucceeded(true)
      setTimeout(() => navigate('/payments/mine'), 1500)
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setSavedCardError(err.response.data.message)
      } else {
        setSavedCardError('Could not start payment. Please try again later.')
      }
    } finally {
      setPayingWithSaved(false)
    }
  }

  if (loading) return <p className="text-slate-600">Loading checkout...</p>
  if (error) return <p className="text-red-600">{error}</p>

  if (succeeded) {
    return (
      <div className="flex justify-center">
        <Card className="w-full max-w-md text-center">
          <h1 className="text-xl font-semibold text-slate-900">Payment successful</h1>
          <p className="mt-2 text-slate-600">
            Your payment went through. Your {noun} will be confirmed in a few seconds — confirmation happens when
            Stripe notifies us, so it may take a moment to show up.
          </p>
          <Link to="/payments/mine" className="mt-4 inline-block font-medium text-blue-600 hover:underline">
            View my payments
          </Link>
        </Card>
      </div>
    )
  }

  if (!summary) return null

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-md">
        <h1 className="mb-1 text-xl font-semibold text-slate-900">Checkout</h1>
        <p className="mb-4 text-sm text-slate-600">{summary.title}</p>
        <p className="mb-6 text-2xl font-semibold text-slate-900">
          {summary.amount.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
        </p>

        {savedMethods.length > 0 && (
          <div className="mb-6 flex flex-col gap-3 border-b border-slate-200 pb-6">
            <p className="text-sm font-medium text-slate-700">Pay with a saved card</p>
            {savedMethods.map((m) => (
              <label key={m.id} className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="radio"
                  name="savedMethod"
                  checked={selectedSavedId === m.id}
                  onChange={() => setSelectedSavedId(m.id)}
                />
                {m.brand.toUpperCase()} &bull;&bull;&bull;&bull; {m.last4} (exp {m.expMonth}/{m.expYear})
                {m.defaultCard ? ' · Default' : ''}
              </label>
            ))}
            {savedCardError && <p className="text-sm text-red-600">{savedCardError}</p>}
            <Button type="button" onClick={handlePaySaved} disabled={payingWithSaved || !selectedSavedId}>
              {payingWithSaved ? 'Processing...' : 'Pay with selected card'}
            </Button>
            {!showNewCard && (
              <Button type="button" variant="secondary" onClick={() => setShowNewCard(true)}>
                Use a new card instead
              </Button>
            )}
          </div>
        )}

        {showNewCard && intentLoading && <p className="text-sm text-slate-600">Preparing payment form...</p>}
        {showNewCard && intentError && <p className="text-sm text-red-600">{intentError}</p>}
        {showNewCard && clientSecret && (
          <Elements stripe={stripePromise} options={{ clientSecret }}>
            <PaymentForm
              onSuccess={() => {
                setSucceeded(true)
                setTimeout(() => navigate('/payments/mine'), 1500)
              }}
            />
          </Elements>
        )}

        <Link to={payLaterPath} className="mt-6 block text-center text-sm font-medium text-blue-600 hover:underline">
          Pay later
        </Link>
      </Card>
    </div>
  )
}
