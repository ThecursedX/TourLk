import axiosClient from './axiosClient'
import type {
  DestinationBrowseParams,
  DestinationRequestDto,
  DestinationResponseDto,
} from '../types/destination'

/** Public — active destinations only. Optional `search` (name) or `region` filter. */
export function browseDestinations(params?: DestinationBrowseParams) {
  return axiosClient
    .get<DestinationResponseDto[]>('/destinations', { params })
    .then((res) => res.data)
}

export function getDestinationById(id: number) {
  return axiosClient.get<DestinationResponseDto>(`/destinations/${id}`).then((res) => res.data)
}

/** ADMIN — includes INACTIVE destinations. */
export function getAllDestinations() {
  return axiosClient.get<DestinationResponseDto[]>('/destinations/all').then((res) => res.data)
}

export function createDestination(data: DestinationRequestDto) {
  return axiosClient.post<DestinationResponseDto>('/destinations', data).then((res) => res.data)
}

export function updateDestination(id: number, data: DestinationRequestDto) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}`, data).then((res) => res.data)
}

export function deactivateDestination(id: number) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/deactivate`).then((res) => res.data)
}

export function reactivateDestination(id: number) {
  return axiosClient.put<DestinationResponseDto>(`/destinations/${id}/reactivate`).then((res) => res.data)
}
