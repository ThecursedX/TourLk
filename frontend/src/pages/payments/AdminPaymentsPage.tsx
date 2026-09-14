import { useEffect, useState } from 'react'
import { getAllPayments, refundPayment } from '../../api/paymentApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import PaymentStatusBadge from '../../components/payments/PaymentStatusBadge'
import { PAYABLE_TYPE_LABELS, type PaymentResponseDto } from '../../types/payment'

export default function AdminPaymentsPage() {
  const [payments, setPayments] = useState<PaymentResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    setError(null)
    getAllPayments()
      .then(setPayments)
      .catch(() => setError('Could not load payments. Please try again later.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const handleRefund = async (id: number) => {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await refundPayment(id)
      setPayments((prev) => prev.map((p) => (p.id === id ? updated : p)))
    } catch {
      setActionError('That payment could not be refunded. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Payments</h1>
        <p className="mt-1 text-slate-600">All payments across the platform.</p>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      {!loading && !error && payments.length === 0 && <p className="text-slate-600">No payments yet.</p>}

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
                <Button
                  variant="secondary"
                  disabled={busyId === payment.id}
                  onClick={() => handleRefund(payment.id)}
                >
                  Refund
                </Button>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
