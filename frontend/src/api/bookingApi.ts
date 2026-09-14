import axiosClient from './axiosClient'
import type { BookingRequestDto, BookingResponseDto, RescheduleRequestDto } from '../types/booking'

export function createBooking(data: BookingRequestDto) {
  return axiosClient.post<BookingResponseDto>('/bookings', data).then((res) => res.data)
}

export function getBookingById(id: number) {
  return axiosClient.get<BookingResponseDto>(`/bookings/${id}`).then((res) => res.data)
}

export function getMyBookings() {
  return axiosClient.get<BookingResponseDto[]>('/bookings/mine').then((res) => res.data)
}

export function getBookingsByPackage(packageId: number) {
  return axiosClient.get<BookingResponseDto[]>(`/bookings/package/${packageId}`).then((res) => res.data)
}

export function confirmBooking(id: number) {
  return axiosClient.put<BookingResponseDto>(`/bookings/${id}/confirm`).then((res) => res.data)
}

export function requestReschedule(id: number, data: RescheduleRequestDto) {
  return axiosClient.put<BookingResponseDto>(`/bookings/${id}/reschedule`, data).then((res) => res.data)
}

export function approveReschedule(id: number) {
  return axiosClient.put<BookingResponseDto>(`/bookings/${id}/reschedule/approve`).then((res) => res.data)
}

export function rejectReschedule(id: number) {
  return axiosClient.put<BookingResponseDto>(`/bookings/${id}/reschedule/reject`).then((res) => res.data)
}

export function cancelBooking(id: number) {
  return axiosClient.put<BookingResponseDto>(`/bookings/${id}/cancel`).then((res) => res.data)
}

export function completeBooking(id: number) {
  return axiosClient.put<BookingResponseDto>(`/bookings/${id}/complete`).then((res) => res.data)
}
