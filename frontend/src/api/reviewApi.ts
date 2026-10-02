import axiosClient from './axiosClient'
import type {
  GuideReviewStatsDto,
  RatingSummaryDto,
  ReviewEditHistoryResponseDto,
  ReviewRequestDto,
  ReviewResponseDto,
  ReviewStatus,
  ReviewableType,
} from '../types/review'

export function createReview(data: ReviewRequestDto) {
  return axiosClient.post<ReviewResponseDto>('/reviews', data).then((res) => res.data)
}

export function updateReview(id: number, data: ReviewRequestDto) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}`, data).then((res) => res.data)
}

export function deleteReview(id: number) {
  return axiosClient.delete<void>(`/reviews/${id}`).then((res) => res.data)
}

export function getReviewsFor(type: ReviewableType, id: number) {
  return axiosClient.get<ReviewResponseDto[]>('/reviews', { params: { type, id } }).then((res) => res.data)
}

export function getRatingSummary(type: ReviewableType, id: number) {
  return axiosClient.get<RatingSummaryDto>('/reviews/summary', { params: { type, id } }).then((res) => res.data)
}

export function getMyReviews() {
  return axiosClient.get<ReviewResponseDto[]>('/reviews/mine').then((res) => res.data)
}

export function getReviewEditHistory(id: number) {
  return axiosClient.get<ReviewEditHistoryResponseDto[]>(`/reviews/${id}/history`).then((res) => res.data)
}

export function replyToReview(id: number, reply: string) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/reply`, { reply }).then((res) => res.data)
}

export function removeGuideReply(id: number) {
  return axiosClient.delete<ReviewResponseDto>(`/reviews/${id}/reply`).then((res) => res.data)
}

export function getGuideReviewStats() {
  return axiosClient.get<GuideReviewStatsDto>('/reviews/guide/stats').then((res) => res.data)
}

/** ADMIN moderation view. Omit status for everything. */
export function getAllReviewsForAdmin(status?: ReviewStatus) {
  return axiosClient
    .get<ReviewResponseDto[]>('/reviews/admin', { params: status === undefined ? undefined : { status } })
    .then((res) => res.data)
}

export function reportReview(id: number) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/report`).then((res) => res.data)
}

export function unreportReview(id: number) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/unreport`).then((res) => res.data)
}

export function hideReview(id: number) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/hide`).then((res) => res.data)
}

export function unhideReview(id: number) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/unhide`).then((res) => res.data)
}
