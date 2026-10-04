import axiosClient from './axiosClient'
import type {
  InvoiceResponseDto,
  PaymentFilters,
  PaymentIntentResponseDto,
  PaymentRequestDto,
  PaymentResponseDto,
  PaymentSummaryDto,
} from '../types/payment'

export function createPaymentIntent(data: PaymentRequestDto) {
  return axiosClient.post<PaymentIntentResponseDto>('/payments/intent', data).then((res) => res.data)
}

export function getMyPayments() {
  return axiosClient.get<PaymentResponseDto[]>('/payments/mine').then((res) => res.data)
}

export function getAllPayments(filters: PaymentFilters = {}) {
  const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value))
  return axiosClient.get<PaymentResponseDto[]>('/payments', { params }).then((res) => res.data)
}

export function getPaymentSummary() {
  return axiosClient.get<PaymentSummaryDto>('/payments/summary').then((res) => res.data)
}

export function getPaymentById(id: number) {
  return axiosClient.get<PaymentResponseDto>(`/payments/${id}`).then((res) => res.data)
}

export function getInvoice(paymentId: number) {
  return axiosClient.get<InvoiceResponseDto>(`/payments/${paymentId}/invoice`).then((res) => res.data)
}

export function refundPayment(id: number) {
  return axiosClient.put<PaymentResponseDto>(`/payments/${id}/refund`).then((res) => res.data)
}

/** Admin: deletes a PENDING / FAILED / REFUNDED payment record (never touches the booking it was for). */
export function deletePayment(id: number) {
  return axiosClient.delete<void>(`/payments/${id}`).then(() => undefined)
}

// Matches com.tourlk.dto.BulkDeletePaymentsResultDto
export interface BulkDeletePaymentsResult {
  deleted: number
  skipped: { id: number; reason: string }[]
}

/** Admin: up to 200 ids; protected rows are skipped (with a reason) instead of failing the call. */
export function bulkDeletePayments(ids: number[]) {
  return axiosClient
    .delete<BulkDeletePaymentsResult>('/payments/bulk', { data: { ids } })
    .then((res) => res.data)
}
