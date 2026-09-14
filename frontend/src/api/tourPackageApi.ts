import axiosClient from './axiosClient'
import type {
  TourPackageRequestDto,
  TourPackageResponseDto,
  TourPackageSearchParams,
} from '../types/tourPackage'

export function browsePackages(params?: TourPackageSearchParams) {
  return axiosClient
    .get<TourPackageResponseDto[]>('/packages', { params })
    .then((res) => res.data)
}

export function getPackageById(id: number) {
  return axiosClient.get<TourPackageResponseDto>(`/packages/${id}`).then((res) => res.data)
}

export function getMyPackages() {
  return axiosClient.get<TourPackageResponseDto[]>('/packages/mine').then((res) => res.data)
}

export function getPendingApprovalPackages() {
  return axiosClient.get<TourPackageResponseDto[]>('/packages/pending-approval').then((res) => res.data)
}

export function createPackage(data: TourPackageRequestDto) {
  return axiosClient.post<TourPackageResponseDto>('/packages', data).then((res) => res.data)
}

export function updatePackage(id: number, data: TourPackageRequestDto) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}`, data).then((res) => res.data)
}

export function submitPackageForApproval(id: number) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/submit`).then((res) => res.data)
}

export function approvePackage(id: number) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/approve`).then((res) => res.data)
}

export function rejectPackage(id: number) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/reject`).then((res) => res.data)
}

export function deactivatePackage(id: number) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/deactivate`).then((res) => res.data)
}

export function reactivatePackage(id: number) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/reactivate`).then((res) => res.data)
}

export function archivePackage(id: number) {
  return axiosClient.delete<TourPackageResponseDto>(`/packages/${id}`).then((res) => res.data)
}
