export type ReviewableType = 'TOUR_PACKAGE' | 'ACCOMMODATION' | 'VEHICLE'

// Matches com.tourlk.dto.ReviewRequestDto
export interface ReviewRequestDto {
  reviewableType: ReviewableType
  reviewableId: number
  sourceBookingId: number
  rating: number
  comment?: string
}

// Matches com.tourlk.dto.ReviewResponseDto
export interface ReviewResponseDto {
  id: number
  reviewableType: ReviewableType
  reviewableId: number
  sourceBookingId: number
  reviewerId: number
  reviewerName: string
  rating: number
  comment: string | null
  createdAt: string
  updatedAt: string
}

// Matches com.tourlk.dto.RatingSummaryDto
export interface RatingSummaryDto {
  reviewableType: ReviewableType
  reviewableId: number
  averageRating: number
  totalReviews: number
}
