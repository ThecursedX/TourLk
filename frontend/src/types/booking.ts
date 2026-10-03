export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'RESCHEDULE_REQUESTED'
  | 'RESCHEDULED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'

// Matches com.tourlk.dto.BookingRequestDto
export interface BookingRequestDto {
  tourPackageId: number
  travelDate: string
  numberOfTravelers: number
  specialRequests?: string
}

// Matches com.tourlk.dto.RescheduleRequestDto
export interface RescheduleRequestDto {
  newTravelDate: string
}

// Matches com.tourlk.dto.BookingPackageSummaryDto
export interface BookingPackageSummaryDto {
  id: number
  title: string
  destination: string
  price: number
}

// Matches com.tourlk.dto.BookingResponseDto
export interface BookingResponseDto {
  id: number
  tourPackage: BookingPackageSummaryDto
  touristId: number
  touristName: string
  travelDate: string
  numberOfTravelers: number
  /** Fixed when the booking was made — what the tourist pays, even if the package is repriced later. */
  totalPrice: number
  specialRequests: string | null
  status: BookingStatus
  previousTravelDate: string | null
  requestedTravelDate: string | null
  /** Set when status is REJECTED. */
  rejectionReason: string | null
  /** True once a SUCCEEDED payment exists for this booking. */
  paid: boolean
  createdAt: string
}

// Matches com.tourlk.dto.RejectBookingRequestDto
export interface RejectBookingRequestDto {
  reason: string
}

// Matches com.tourlk.dto.CancellationPreviewResponseDto
export interface CancellationPreviewResponseDto {
  refundPercent: number
  refundAmount: number
  ruleText: string
  hasPayment: boolean
}
