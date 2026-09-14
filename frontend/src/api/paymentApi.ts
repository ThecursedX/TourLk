import axiosClient from './axiosClient'
import type {
  InvoiceResponseDto,
  PaymentIntentResponseDto,
  PaymentRequestDto,
  PaymentResponseDto,
} from '../types/payment'

export function createPaymentIntent(data: PaymentRequestDto) {
  return axiosClient.post<PaymentIntentResponseDto>('/payments/intent', data).then((res) => res.data)
}

export function getMyPayments() {
  return axiosClient.get<PaymentResponseDto[]>('/payments/mine').then((res) => res.data)
}

export function getAllPayments() {
  return axiosClient.get<PaymentResponseDto[]>('/payments').then((res) => res.data)
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
