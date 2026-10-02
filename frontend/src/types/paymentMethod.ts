// Matches com.tourlk.dto.SetupIntentResponseDto
export interface SetupIntentResponseDto {
  clientSecret: string
}

// Matches com.tourlk.dto.SavePaymentMethodRequestDto
export interface SavePaymentMethodRequestDto {
  paymentMethodId: string
}

// Matches com.tourlk.dto.PaymentMethodResponseDto
export interface PaymentMethodResponseDto {
  id: number
  brand: string
  last4: string
  expMonth: number
  expYear: number
  defaultCard: boolean
}
