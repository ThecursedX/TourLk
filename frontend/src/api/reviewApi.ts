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
