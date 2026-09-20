import axiosClient from './axiosClient'
import type { AuthResponseDto } from '../types/auth'
import type { UpdateProfileRequestDto, UserResponseDto } from '../types/user'

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
