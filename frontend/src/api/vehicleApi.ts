import axiosClient from './axiosClient'
import type { VehicleRequestDto, VehicleResponseDto, VehicleType } from '../types/vehicle'

export function browseVehicles(vehicleType?: VehicleType, minSeatingCapacity?: number) {
  return axiosClient
    .get<VehicleResponseDto[]>('/vehicles', { params: { vehicleType, minSeatingCapacity } })
    .then((res) => res.data)
}

export function getVehicleById(id: number) {
  return axiosClient.get<VehicleResponseDto>(`/vehicles/${id}`).then((res) => res.data)
}

export function getMyVehicles() {
  return axiosClient.get<VehicleResponseDto[]>('/vehicles/mine').then((res) => res.data)
}

export function getPendingApprovalVehicles() {
  return axiosClient.get<VehicleResponseDto[]>('/vehicles/pending-approval').then((res) => res.data)
}

export function createVehicle(data: VehicleRequestDto) {
  return axiosClient.post<VehicleResponseDto>('/vehicles', data).then((res) => res.data)
}

export function updateVehicle(id: number, data: VehicleRequestDto) {
  return axiosClient.put<VehicleResponseDto>(`/vehicles/${id}`, data).then((res) => res.data)
}

export function approveVehicle(id: number) {
  return axiosClient.put<VehicleResponseDto>(`/vehicles/${id}/approve`).then((res) => res.data)
}

export function rejectVehicle(id: number) {
  return axiosClient.put<VehicleResponseDto>(`/vehicles/${id}/reject`).then((res) => res.data)
}

export function deactivateVehicle(id: number) {
  return axiosClient.put<VehicleResponseDto>(`/vehicles/${id}/deactivate`).then((res) => res.data)
}

export function reactivateVehicle(id: number) {
  return axiosClient.put<VehicleResponseDto>(`/vehicles/${id}/reactivate`).then((res) => res.data)
}

export function archiveVehicle(id: number) {
  return axiosClient.delete<VehicleResponseDto>(`/vehicles/${id}`).then((res) => res.data)
}
