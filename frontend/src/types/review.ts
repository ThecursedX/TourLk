export type ReviewableType = 'TOUR_PACKAGE' | 'ACCOMMODATION' | 'VEHICLE'

// Matches com.tourlk.enums.ReviewStatus
export type ReviewStatus = 'PUBLISHED' | 'EDITED' | 'REPORTED' | 'HIDDEN' | 'DELETED'

// Matches com.tourlk.dto.ReviewRequestDto
export interface ReviewRequestDto {
  reviewableType: ReviewableType
  reviewableId: number
  sourceBookingId: number
  rating: number
  comment: string
  imageUrls?: string[]
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
  imageUrls: string[]
  status: ReviewStatus
  editable: boolean
  editDeadline: string | null
  guideReply: string | null
  guideReplyAt: string | null
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

// Matches com.tourlk.dto.GuideReplyRequestDto
export interface GuideReplyRequestDto {
  reply: string
}

// Matches com.tourlk.dto.ReviewEditHistoryResponseDto
export interface ReviewEditHistoryResponseDto {
  id: number
  oldRating: number
  oldComment: string | null
  editedAt: string
}

// Matches com.tourlk.dto.GuideReviewStatsDto
export interface GuideReviewStatsDto {
  averageRating: number
  totalReviews: number
  countByStar: Record<number, number>
  replyRate: number
}
