import axiosClient from './axiosClient'
import type { AccommodationRequestDto, AccommodationResponseDto, RoomRequestDto, RoomResponseDto } from '../types/accommodation'

export function browseAccommodations(locationId?: number) {
  return axiosClient
    .get<AccommodationResponseDto[]>('/accommodations', {
      params: locationId ? { locationId } : undefined,
    })
    .then((res) => res.data)
}

export function getAccommodationById(id: number) {
  return axiosClient.get<AccommodationResponseDto>(`/accommodations/${id}`).then((res) => res.data)
}

export function getMyAccommodations() {
  return axiosClient.get<AccommodationResponseDto[]>('/accommodations/mine').then((res) => res.data)
}

export function createAccommodation(data: AccommodationRequestDto) {
  return axiosClient.post<AccommodationResponseDto>('/accommodations', data).then((res) => res.data)
}

export function updateAccommodation(id: number, data: AccommodationRequestDto) {
  return axiosClient.put<AccommodationResponseDto>(`/accommodations/${id}`, data).then((res) => res.data)
}

export function submitAccommodationForApproval(id: number) {
  return axiosClient.put<AccommodationResponseDto>(`/accommodations/${id}/submit`).then((res) => res.data)
}

export function approveAccommodation(id: number) {
  return axiosClient.put<AccommodationResponseDto>(`/accommodations/${id}/approve`).then((res) => res.data)
}

export function rejectAccommodation(id: number) {
  return axiosClient.put<AccommodationResponseDto>(`/accommodations/${id}/reject`).then((res) => res.data)
}

export function deactivateAccommodation(id: number) {
  return axiosClient.put<AccommodationResponseDto>(`/accommodations/${id}/deactivate`).then((res) => res.data)
}

export function reactivateAccommodation(id: number) {
  return axiosClient.put<AccommodationResponseDto>(`/accommodations/${id}/reactivate`).then((res) => res.data)
}

export function archiveAccommodation(id: number) {
  return axiosClient.delete<AccommodationResponseDto>(`/accommodations/${id}`).then((res) => res.data)
}

export function addRoom(accommodationId: number, data: RoomRequestDto) {
  return axiosClient
    .post<RoomResponseDto>(`/accommodations/${accommodationId}/rooms`, data)
    .then((res) => res.data)
}

export function updateRoom(roomId: number, data: RoomRequestDto) {
  return axiosClient.put<RoomResponseDto>(`/rooms/${roomId}`, data).then((res) => res.data)
}

export function removeRoom(roomId: number) {
  return axiosClient.delete<void>(`/rooms/${roomId}`).then((res) => res.data)
}

export function getPendingApprovalAccommodations() {
  return axiosClient
    .get<AccommodationResponseDto[]>('/accommodations/pending-approval')
    .then((res) => res.data)
}
