import axiosClient from './axiosClient'
import type { AuthResponseDto, LoginRequestDto, RegisterRequestDto } from '../types/auth'

export function loginRequest(data: LoginRequestDto) {
  return axiosClient.post<AuthResponseDto>('/auth/login', data).then((res) => res.data)
}

export function registerRequest(data: RegisterRequestDto) {
  return axiosClient.post<AuthResponseDto>('/auth/register', data).then((res) => res.data)
}
