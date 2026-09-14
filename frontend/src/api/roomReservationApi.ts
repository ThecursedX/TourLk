import axiosClient from './axiosClient'
import type { RoomReservationRequestDto, RoomReservationResponseDto } from '../types/accommodation'

export function createReservation(data: RoomReservationRequestDto) {
  return axiosClient.post<RoomReservationResponseDto>('/reservations', data).then((res) => res.data)
}

export function getReservationById(id: number) {
  return axiosClient.get<RoomReservationResponseDto>(`/reservations/${id}`).then((res) => res.data)
}

export function getMyReservations() {
  return axiosClient.get<RoomReservationResponseDto[]>('/reservations/mine').then((res) => res.data)
}

export function getReservationsByRoom(roomId: number) {
  return axiosClient
    .get<RoomReservationResponseDto[]>(`/reservations/room/${roomId}`)
    .then((res) => res.data)
}

export function confirmReservation(id: number) {
  return axiosClient.put<RoomReservationResponseDto>(`/reservations/${id}/confirm`).then((res) => res.data)
}

export function cancelReservation(id: number) {
  return axiosClient.put<RoomReservationResponseDto>(`/reservations/${id}/cancel`).then((res) => res.data)
}

export function completeReservation(id: number) {
  return axiosClient.put<RoomReservationResponseDto>(`/reservations/${id}/complete`).then((res) => res.data)
}
