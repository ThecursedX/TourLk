import axiosClient from './axiosClient'
import type {
  PackageDepartureRequestDto,
  PackageDepartureResponseDto,
  PackageStatus,
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

/** Admin only: every package, optionally of one status, newest first. */
export function getAdminPackages(status?: PackageStatus) {
  return axiosClient
    .get<TourPackageResponseDto[]>('/packages/admin', { params: { status } })
    .then((res) => res.data)
}

export function createPackage(data: TourPackageRequestDto) {
  return axiosClient.post<TourPackageResponseDto>('/packages', data).then((res) => res.data)
}

/**
 * Changing price, duration or capacity of an ACTIVE package with upcoming
 * bookings fails with 409 + code CONFIRMATION_REQUIRED unless
 * confirmChanges is true.
 */
export function updatePackage(id: number, data: TourPackageRequestDto, confirmChanges = false) {
  return axiosClient
    .put<TourPackageResponseDto>(`/packages/${id}`, data, { params: confirmChanges ? { confirmChanges } : undefined })
    .then((res) => res.data)
}

export function submitPackageForApproval(id: number) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/submit`).then((res) => res.data)
}

export function approvePackage(id: number) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/approve`).then((res) => res.data)
}

export function rejectPackage(id: number, reason: string) {
  return axiosClient.put<TourPackageResponseDto>(`/packages/${id}/reject`, { reason }).then((res) => res.data)
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

/** Upcoming departures only (dated after today), earliest first. Public. */
export function getPackageDepartures(packageId: number) {
  return axiosClient
    .get<PackageDepartureResponseDto[]>(`/packages/${packageId}/departures`)
    .then((res) => res.data)
}

export function addPackageDeparture(packageId: number, data: PackageDepartureRequestDto) {
  return axiosClient
    .post<PackageDepartureResponseDto>(`/packages/${packageId}/departures`, data)
    .then((res) => res.data)
}

export function deletePackageDeparture(packageId: number, departureId: number) {
  return axiosClient.delete<void>(`/packages/${packageId}/departures/${departureId}`).then(() => undefined)
}
