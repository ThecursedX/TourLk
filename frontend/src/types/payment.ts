export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED' | 'REFUND_PENDING' | 'CANCELLED'

export type PayableType = 'BOOKING' | 'ROOM_RESERVATION' | 'VEHICLE_HIRE'

export const PAYABLE_TYPE_LABELS: Record<PayableType, string> = {
  BOOKING: 'Tour Package Booking',
  ROOM_RESERVATION: 'Room Reservation',
  VEHICLE_HIRE: 'Vehicle Hire',
}

// Matches com.tourlk.dto.PaymentRequestDto
export interface PaymentRequestDto {
  payableType: PayableType
  payableId: number
  amount: number
  /** Optional: id of a SavedPaymentMethod to charge instead of collecting a new card. */
  savedPaymentMethodId?: number
}

// Matches com.tourlk.dto.PaymentResponseDto
export interface PaymentResponseDto {
  id: number
  amount: number
  currency: string
  status: PaymentStatus
  payableType: PayableType
  payableId: number
  payerId: number
  payerName: string
  payerEmail: string
  /** How much was actually refunded, if any — may be less than `amount` under a partial refund. */
  refundAmount: number | null
  createdAt: string
}

// Optional filters for GET /api/payments (admin)
export interface PaymentFilters {
  status?: PaymentStatus
  payableType?: PayableType
  search?: string
  /** ISO date (yyyy-MM-dd), inclusive */
  from?: string
  /** ISO date (yyyy-MM-dd), inclusive */
  to?: string
}

// Matches com.tourlk.dto.PaymentSummaryDto
export interface PaymentSummaryDto {
  totalCollected: number
  totalRefunded: number
  pendingCount: number
  failedCount: number
}

// Matches com.tourlk.dto.PaymentIntentResponseDto
export interface PaymentIntentResponseDto {
  clientSecret: string
  paymentId: number
}

// Matches com.tourlk.dto.InvoiceResponseDto
export interface InvoiceResponseDto {
  invoiceNumber: string
  amount: number
  currency: string
  issuedAt: string
  payerName: string
  payableType: PayableType
  payableId: number
}
