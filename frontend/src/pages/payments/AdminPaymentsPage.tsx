import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAllPayments, getPaymentSummary, refundPayment } from '../../api/paymentApi'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import PaymentStatusBadge from '../../components/payments/PaymentStatusBadge'
import {
  PAYABLE_TYPE_LABELS,
  type PayableType,
  type PaymentFilters,
  type PaymentResponseDto,
  type PaymentStatus,
  type PaymentSummaryDto,
} from '../../types/payment'

const PAGE_SIZE = 10

const STATUS_OPTIONS: PaymentStatus[] = ['PENDING', 'SUCCEEDED', 'FAILED', 'REFUND_PENDING', 'REFUNDED', 'CANCELLED']

type SortKey = 'date' | 'amount'
type SortDir = 'asc' | 'desc'

function formatMoney(amount: number, currency = 'usd') {
  return amount.toLocaleString(undefined, { style: 'currency', currency: currency.toUpperCase() })
}

function SummaryTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-slate-900">{value}</p>
    </div>
  )
}

export default function AdminPaymentsPage() {
  const [payments, setPayments] = useState<PaymentResponseDto[]>([])
  const [summary, setSummary] = useState<PaymentSummaryDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const [status, setStatus] = useState<PaymentStatus | ''>('')
  const [payableType, setPayableType] = useState<PayableType | ''>('')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const [sortKey, setSortKey] = useState<SortKey>('date')
  const [sortDir, setSortDir] = useState<SortDir>('desc')
  const [page, setPage] = useState(1)

  // Debounce the free-text search so we don't hit the API on every keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setSearch(searchInput.trim()), 300)
    return () => clearTimeout(timer)
  }, [searchInput])

  const loadSummary = useCallback(() => {
    getPaymentSummary()
      .then(setSummary)
      .catch(() => setSummary(null))
  }, [])

  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  useEffect(() => {
    const filters: PaymentFilters = {
      status: status || undefined,
      payableType: payableType || undefined,
      search: search || undefined,
      from: from || undefined,
      to: to || undefined,
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    getAllPayments(filters)
      .then((data) => {
        if (!cancelled) setPayments(data)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load payments. Please try again later.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [status, payableType, search, from, to])

  useEffect(() => {
    setPage(1)
  }, [status, payableType, search, from, to, sortKey, sortDir])

  const sorted = useMemo(() => {
    const direction = sortDir === 'asc' ? 1 : -1
    return [...payments].sort((a, b) => {
      const diff =
        sortKey === 'amount'
          ? a.amount - b.amount
          : new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      return diff * direction
    })
  }, [payments, sortKey, sortDir])

  const pageCount = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount)
  const pageRows = sorted.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir('desc')
    }
  }

  const sortIndicator = (key: SortKey) => (sortKey === key ? (sortDir === 'asc' ? ' ▲' : ' ▼') : '')

  const handleRefund = async (payment: PaymentResponseDto) => {
    const confirmed = window.confirm(
      `Refund ${formatMoney(payment.amount, payment.currency)} to ${payment.payerName}? This cannot be undone.`,
    )
    if (!confirmed) return

    setActionError(null)
    setBusyId(payment.id)
    try {
      const updated = await refundPayment(payment.id)
      setPayments((prev) => prev.map((p) => (p.id === payment.id ? updated : p)))
      loadSummary()
    } catch {
      setActionError('That payment could not be refunded. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  const clearFilters = () => {
    setStatus('')
    setPayableType('')
    setSearchInput('')
    setSearch('')
    setFrom('')
    setTo('')
  }

  const hasFilters = Boolean(status || payableType || search || from || to)
  const thClass = 'whitespace-nowrap px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide text-slate-500'
  const tdClass = 'whitespace-nowrap px-3 py-3 text-sm text-slate-700'

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Payments</h1>
        <p className="mt-1 text-slate-600">All payments across the platform.</p>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <SummaryTile label="Total collected" value={summary ? formatMoney(summary.totalCollected) : '—'} />
        <SummaryTile label="Total refunded" value={summary ? formatMoney(summary.totalRefunded) : '—'} />
        <SummaryTile label="Pending" value={summary ? String(summary.pendingCount) : '—'} />
        <SummaryTile label="Failed" value={summary ? String(summary.failedCount) : '—'} />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-6">
        <Select
          id="payment-status"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as PaymentStatus | '')}
        >
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option.replace('_', ' ')}
            </option>
          ))}
        </Select>
        <Select
          id="payment-type"
          label="Type"
          value={payableType}
          onChange={(e) => setPayableType(e.target.value as PayableType | '')}
        >
          <option value="">All types</option>
          {(Object.keys(PAYABLE_TYPE_LABELS) as PayableType[]).map((type) => (
            <option key={type} value={type}>
              {PAYABLE_TYPE_LABELS[type]}
            </option>
          ))}
        </Select>
        <div className="sm:col-span-2">
          <Input
            id="payment-search"
            label="Search"
            placeholder="Payer name, email or reference #"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>
        <Input id="payment-from" label="From" type="date" value={from} max={to || undefined}
          onChange={(e) => setFrom(e.target.value)} />
        <Input id="payment-to" label="To" type="date" value={to} min={from || undefined}
          onChange={(e) => setTo(e.target.value)} />
      </div>
      {hasFilters && (
        <div>
          <Button variant="ghost" onClick={clearFilters}>
            Clear filters
          </Button>
        </div>
      )}

      {error && <p className="text-red-600">{error}</p>}
      {actionError && <p className="text-red-600">{actionError}</p>}

      <div className="overflow-x-auto rounded-xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200">
          <thead className="bg-slate-50">
            <tr>
              <th className={thClass}>ID</th>
              <th className={thClass} aria-sort={sortKey === 'date' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}>
                <button type="button" onClick={() => toggleSort('date')} className="uppercase tracking-wide">
                  Date{sortIndicator('date')}
                </button>
              </th>
              <th className={thClass}>Payer</th>
              <th className={thClass}>Type</th>
              <th className={thClass}>Reference</th>
              <th className={thClass} aria-sort={sortKey === 'amount' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}>
                <button type="button" onClick={() => toggleSort('amount')} className="uppercase tracking-wide">
                  Amount{sortIndicator('amount')}
                </button>
              </th>
              <th className={thClass}>Refunded</th>
              <th className={thClass}>Status</th>
              <th className={thClass}>Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {loading && (
              <tr>
                <td colSpan={9} className="px-3 py-8 text-center text-sm text-slate-600">
                  Loading payments...
                </td>
              </tr>
            )}
            {!loading && !error && pageRows.length === 0 && (
              <tr>
                <td colSpan={9} className="px-3 py-8 text-center text-sm text-slate-600">
                  {hasFilters ? 'No payments match these filters.' : 'No payments yet.'}
                </td>
              </tr>
            )}
            {!loading &&
              pageRows.map((payment) => (
                <tr key={payment.id}>
                  <td className={tdClass}>#{payment.id}</td>
                  <td className={tdClass}>{new Date(payment.createdAt).toLocaleDateString()}</td>
                  <td className={tdClass}>
                    <p className="font-medium text-slate-900">{payment.payerName}</p>
                    <p className="text-xs text-slate-500">{payment.payerEmail}</p>
                  </td>
                  <td className={tdClass}>{PAYABLE_TYPE_LABELS[payment.payableType]}</td>
                  <td className={tdClass}>#{payment.payableId}</td>
                  <td className={tdClass}>{formatMoney(payment.amount, payment.currency)}</td>
                  <td className={tdClass}>
                    {payment.refundAmount != null && (payment.status === 'REFUNDED' || payment.status === 'REFUND_PENDING')
                      ? formatMoney(payment.refundAmount, payment.currency)
                      : '—'}
                  </td>
                  <td className={tdClass}>
                    <PaymentStatusBadge status={payment.status} />
                  </td>
                  <td className={tdClass}>
                    <div className="flex items-center gap-3">
                      <Link to={`/payments/${payment.id}/invoice`} className="font-medium text-blue-600 hover:underline">
                        View invoice
                      </Link>
                      {payment.status === 'SUCCEEDED' && (
                        <Button
                          variant="secondary"
                          disabled={busyId === payment.id}
                          onClick={() => handleRefund(payment)}
                        >
                          Refund
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {!loading && sorted.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-600">
          <span>
            Showing {(currentPage - 1) * PAGE_SIZE + 1}–{Math.min(currentPage * PAGE_SIZE, sorted.length)} of{' '}
            {sorted.length}
          </span>
          <div className="flex items-center gap-2">
            <Button variant="secondary" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}>
              Previous
            </Button>
            <span>
              Page {currentPage} of {pageCount}
            </span>
            <Button variant="secondary" disabled={currentPage >= pageCount} onClick={() => setPage(currentPage + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
