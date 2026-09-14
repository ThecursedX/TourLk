import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getInvoice } from '../../api/paymentApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import { PAYABLE_TYPE_LABELS, type InvoiceResponseDto } from '../../types/payment'

export default function InvoicePage() {
  const { id } = useParams<{ id: string }>()
  const [invoice, setInvoice] = useState<InvoiceResponseDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getInvoice(Number(id))
      .then(setInvoice)
      .catch(() => setError('Could not load this invoice. Please try again later.'))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!invoice) return null

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between print:hidden">
        <Link to="/payments/mine" className="text-sm font-medium text-blue-600 hover:underline">
          &larr; Back to my payments
        </Link>
        <Button onClick={() => window.print()}>Download / Print</Button>
      </div>

      <Card className="mx-auto w-full max-w-2xl">
        <div className="flex items-start justify-between border-b border-slate-200 pb-4">
          <div>
            <h1 className="text-xl font-semibold text-slate-900">TourLK</h1>
            <p className="text-sm text-slate-500">Invoice</p>
          </div>
          <div className="text-right">
            <p className="font-medium text-slate-900">{invoice.invoiceNumber}</p>
            <p className="text-sm text-slate-500">{new Date(invoice.issuedAt).toLocaleDateString()}</p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 py-6">
          <div>
            <dt className="text-xs uppercase text-slate-500">Billed to</dt>
            <dd className="text-slate-900">{invoice.payerName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-slate-500">For</dt>
            <dd className="text-slate-900">
              {PAYABLE_TYPE_LABELS[invoice.payableType]} #{invoice.payableId}
            </dd>
          </div>
        </div>

        <div className="flex items-center justify-between border-t border-slate-200 pt-4">
          <span className="text-slate-600">Total paid</span>
          <span className="text-2xl font-semibold text-slate-900">
            {invoice.amount.toLocaleString(undefined, {
              style: 'currency',
              currency: invoice.currency.toUpperCase(),
            })}
          </span>
        </div>
      </Card>
    </div>
  )
}
