export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'RESCHEDULE_REQUESTED'
  | 'RESCHEDULED'
  | 'COMPLETED'
  | 'CANCELLED'

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
  specialRequests: string | null
  status: BookingStatus
  previousTravelDate: string | null
  requestedTravelDate: string | null
  createdAt: string
}
