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
