import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyPayments } from '../../api/paymentApi'
import Card from '../../components/ui/Card'
import PaymentStatusBadge from '../../components/payments/PaymentStatusBadge'
import { PAYABLE_TYPE_LABELS, type PaymentResponseDto } from '../../types/payment'

export default function MyPaymentsPage() {
  const [payments, setPayments] = useState<PaymentResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    getMyPayments()
      .then(setPayments)
      .catch(() => setError('Could not load your payments. Please try again later.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Payments</h1>
        <p className="mt-1 text-slate-600">Your payment history for bookings and reservations.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && payments.length === 0 && (
        <p className="text-slate-600">You haven't made any payments yet.</p>
      )}

      <div className="flex flex-col gap-3">
        {payments.map((payment) => (
          <Card key={payment.id} className="flex items-center justify-between gap-4">
            <div>
              <p className="font-medium text-slate-900">
                {PAYABLE_TYPE_LABELS[payment.payableType]} #{payment.payableId}
              </p>
              <p className="text-sm text-slate-600">
                {payment.amount.toLocaleString(undefined, {
                  style: 'currency',
                  currency: payment.currency.toUpperCase(),
                })}{' '}
                · {new Date(payment.createdAt).toLocaleDateString()}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <PaymentStatusBadge status={payment.status} />
              {payment.status === 'SUCCEEDED' && (
                <Link
                  to={`/payments/${payment.id}/invoice`}
                  className="text-sm font-medium text-blue-600 hover:underline"
                >
                  View Invoice
                </Link>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
