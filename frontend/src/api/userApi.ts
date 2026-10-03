import axiosClient from './axiosClient'
import type { AuthResponseDto } from '../types/auth'
import type {
  ChangePasswordRequestDto,
  LicenceSubmitRequestDto,
  UpdateProfileRequestDto,
  UserResponseDto,
} from '../types/user'

export function getAllUsers(search?: string) {
  return axiosClient
    .get<UserResponseDto[]>('/users', { params: search ? { search } : undefined })
    .then((res) => res.data)
}
export function getUserById(id: number) {
  return axiosClient.get<UserResponseDto>(`/users/${id}`).then((res) => res.data)
}
export function deactivateUser(id: number) {
  return axiosClient.put<UserResponseDto>(`/users/${id}/deactivate`).then((res) => res.data)
}
export function reactivateUser(id: number) {
  return axiosClient.put<UserResponseDto>(`/users/${id}/reactivate`).then((res) => res.data)
}
export function deleteUser(id: number) {
  return axiosClient.delete<void>(`/users/${id}`).then(() => undefined)
}
export function getMyProfile() {
  return axiosClient.get<UserResponseDto>('/users/me').then((res) => res.data)
}
export function updateMyProfile(data: UpdateProfileRequestDto) {
  return axiosClient.put<AuthResponseDto>('/users/me', data).then((res) => res.data)
}
export function changePassword(data: ChangePasswordRequestDto) {
  return axiosClient.put<void>('/users/me/password', data).then(() => undefined)
}
export function submitLicence(data: LicenceSubmitRequestDto) {
  const form = new FormData()
  form.append('licenceNumber', data.licenceNumber)
  form.append('licenceExpiry', data.licenceExpiry)
  form.append('file', data.file)
  return axiosClient.put<UserResponseDto>('/users/me/licence', form).then((res) => res.data)
}

/**
 * The document endpoint needs the JWT, so it is fetched as a blob through axios and shown in a new tab
 * via an object URL (revoked once the tab has had time to load it). The tab is opened synchronously
 * from the click so popup blockers allow it.
 */
export async function openLicenceDocument(userId: number) {
  const tab = window.open('', '_blank')
  try {
    const res = await axiosClient.get<Blob>(`/users/${userId}/licence-document`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    if (tab) {
      tab.location.href = url
    } else {
      window.open(url, '_blank')
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (err) {
    tab?.close()
    throw err
  }
}
export function getPendingLicences() {
  return axiosClient.get<UserResponseDto[]>('/users/licences/pending').then((res) => res.data)
}
export function verifyLicence(id: number) {
  return axiosClient.put<UserResponseDto>(`/users/${id}/licence/verify`).then((res) => res.data)
}
export function rejectLicence(id: number, reason: string) {
  return axiosClient.put<UserResponseDto>(`/users/${id}/licence/reject`, { reason }).then((res) => res.data)
}
