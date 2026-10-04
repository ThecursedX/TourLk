import axiosClient from './axiosClient'
import type {
  PaymentMethodResponseDto,
  SavePaymentMethodRequestDto,
  SetupIntentResponseDto,
} from '../types/paymentMethod'

export function createSetupIntent() {
  return axiosClient.post<SetupIntentResponseDto>('/payment-methods/setup-intent').then((res) => res.data)
}

export function savePaymentMethod(data: SavePaymentMethodRequestDto) {
  return axiosClient.post<PaymentMethodResponseDto>('/payment-methods', data).then((res) => res.data)
}

export function getMyPaymentMethods() {
  return axiosClient.get<PaymentMethodResponseDto[]>('/payment-methods').then((res) => res.data)
}

export function setDefaultPaymentMethod(id: number) {
  return axiosClient.put<PaymentMethodResponseDto>(`/payment-methods/${id}/default`).then((res) => res.data)
}

export function deletePaymentMethod(id: number) {
  return axiosClient.delete<void>(`/payment-methods/${id}`).then(() => undefined)
}
