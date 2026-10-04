import axiosClient from './axiosClient'
import type {
  ClosureImpactDto,
  DestinationBrowseParams,
  DestinationClosureRequestDto,
  DestinationRequestDto,
  DestinationResponseDto,
} from '../types/destination'

/** Public — published and temporarily closed destinations. Optional `search`, `province` or `nearby`+`radiusKm` filter. */
export function browseDestinations(params?: DestinationBrowseParams) {
  return axiosClient
    .get<DestinationResponseDto[]>('/destinations', { params })
    .then((res) => res.data)
}

export function getDestinationById(id: number) {
  return axiosClient.get<DestinationResponseDto>(`/destinations/${id}`).then((res) => res.data)
}

/** ADMIN — every destination, whatever its status. */
export function getAllDestinations() {
  return axiosClient.get<DestinationResponseDto[]>('/destinations/all').then((res) => res.data)
}

export function createDestination(data: DestinationRequestDto) {
  return axiosClient.post<DestinationResponseDto>('/destinations', data).then((res) => res.data)
}

export function updateDestination(id: number, data: DestinationRequestDto) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}`, data).then((res) => res.data)
}

export function submitDestinationForReview(id: number) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/submit`).then((res) => res.data)
}

export function publishDestination(id: number) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/publish`).then((res) => res.data)
}

export function closeDestination(id: number, data: DestinationClosureRequestDto) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/close`, data).then((res) => res.data)
}

/** How many active bookings closing for this window would cancel and fully refund (changes nothing). */
export function getClosureImpact(id: number, from?: string, until?: string) {
  return axiosClient
    .get<ClosureImpactDto>(`/destinations/${id}/closure-impact`, { params: { from: from || undefined, until: until || undefined } })
    .then((res) => res.data)
}

export function reopenDestination(id: number) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/reopen`).then((res) => res.data)
}

export function archiveDestination(id: number) {
  return axiosClient.delete<DestinationResponseDto>(`/destinations/${id}`).then((res) => res.data)
}

export function deactivateDestination(id: number) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/deactivate`).then((res) => res.data)
}

export function reactivateDestination(id: number) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/reactivate`).then((res) => res.data)
}

/** Existing category values across all destinations, for combobox suggestions. */
export function getCategorySuggestions() {
  return axiosClient.get<string[]>('/destinations/categories').then((res) => res.data)
}
