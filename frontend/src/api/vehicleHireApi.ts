import axiosClient from './axiosClient'
import type { VehicleHireRequestDto, VehicleHireResponseDto } from '../types/vehicle'

export function createHire(data: VehicleHireRequestDto) {
  return axiosClient.post<VehicleHireResponseDto>('/hires', data).then((res) => res.data)
}

export function getHireById(id: number) {
  return axiosClient.get<VehicleHireResponseDto>(`/hires/${id}`).then((res) => res.data)
}

export function getMyHires() {
  return axiosClient.get<VehicleHireResponseDto[]>('/hires/mine').then((res) => res.data)
}

export function getHiresByVehicle(vehicleId: number) {
  return axiosClient.get<VehicleHireResponseDto[]>(`/hires/vehicle/${vehicleId}`).then((res) => res.data)
}

export function confirmHire(id: number) {
  return axiosClient.put<VehicleHireResponseDto>(`/hires/${id}/confirm`).then((res) => res.data)
}

export function cancelHire(id: number) {
  return axiosClient.put<VehicleHireResponseDto>(`/hires/${id}/cancel`).then((res) => res.data)
}

export function completeHire(id: number) {
  return axiosClient.put<VehicleHireResponseDto>(`/hires/${id}/complete`).then((res) => res.data)
}
