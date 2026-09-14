import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { loadStripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { getBookingById } from '../../api/bookingApi'
import { getReservationById } from '../../api/roomReservationApi'
import { getHireById } from '../../api/vehicleHireApi'
import { createPaymentIntent } from '../../api/paymentApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import type { ErrorResponse } from '../../types/auth'
import type { PayableType } from '../../types/payment'

const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY)

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
  const [clientSecret, setClientSecret] = useState<string | null>(null)
  const [paymentId, setPaymentId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [succeeded, setSucceeded] = useState(false)

  const normalizedType: PayableType =
    payableType === 'reservation'
      ? 'ROOM_RESERVATION'
      : payableType === 'vehicle_hire'
        ? 'VEHICLE_HIRE'
        : 'BOOKING'

  useEffect(() => {
    if (!payableId) return

    const id = Number(payableId)
    setLoading(true)
    setError(null)

    const loadSummaryAndIntent = async () => {
      try {
        let title: string
        let amount: number

        if (normalizedType === 'BOOKING') {
          const booking = await getBookingById(id)
          title = booking.tourPackage.title
          amount = booking.tourPackage.price * booking.numberOfTravelers
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

        const intent = await createPaymentIntent({
          payableType: normalizedType,
          payableId: id,
          amount,
        })
        setClientSecret(intent.clientSecret)
        setPaymentId(intent.paymentId)
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

    loadSummaryAndIntent()
  }, [payableId, normalizedType])

  if (loading) return <p className="text-slate-600">Loading checkout...</p>
  if (error) return <p className="text-red-600">{error}</p>

  if (succeeded) {
    return (
      <div className="flex justify-center">
        <Card className="w-full max-w-md text-center">
          <h1 className="text-xl font-semibold text-slate-900">Payment successful</h1>
          <p className="mt-2 text-slate-600">Your booking has been confirmed.</p>
          <Link to="/payments/mine" className="mt-4 inline-block font-medium text-blue-600 hover:underline">
            View my payments
          </Link>
        </Card>
      </div>
    )
  }

  if (!summary || !clientSecret || !paymentId) return null

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-md">
        <h1 className="mb-1 text-xl font-semibold text-slate-900">Checkout</h1>
        <p className="mb-4 text-sm text-slate-600">{summary.title}</p>
        <p className="mb-6 text-2xl font-semibold text-slate-900">
          {summary.amount.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
        </p>
        <Elements stripe={stripePromise} options={{ clientSecret }}>
          <PaymentForm
            onSuccess={() => {
              setSucceeded(true)
              setTimeout(() => navigate('/payments/mine'), 1500)
            }}
          />
        </Elements>
      </Card>
    </div>
  )
}
