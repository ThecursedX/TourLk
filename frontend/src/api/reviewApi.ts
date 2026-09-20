import axiosClient from './axiosClient'
import type { RatingSummaryDto, ReviewRequestDto, ReviewResponseDto, ReviewableType } from '../types/review'

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

export function replyToReview(id: number, reply: string) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/reply`, { reply }).then((res) => res.data)
}

export function removeGuideReply(id: number) {
  return axiosClient.delete<ReviewResponseDto>(`/reviews/${id}/reply`).then((res) => res.data)
}

/** ADMIN moderation view. Omit flaggedOnly for everything. */
export function getAllReviewsForAdmin(flaggedOnly?: boolean) {
  return axiosClient
    .get<ReviewResponseDto[]>('/reviews/admin', { params: flaggedOnly === undefined ? undefined : { flaggedOnly } })
    .then((res) => res.data)
}

export function flagReview(id: number) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/flag`).then((res) => res.data)
}

export function unflagReview(id: number) {
  return axiosClient.put<ReviewResponseDto>(`/reviews/${id}/unflag`).then((res) => res.data)
}
