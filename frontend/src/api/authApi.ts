import axiosClient from './axiosClient'
import type {
  AuthResponseDto,
  ForgotPasswordRequestDto,
  LoginRequestDto,
  RegisterRequestDto,
  ResetPasswordRequestDto,
} from '../types/auth'

export function loginRequest(data: LoginRequestDto) {
  return axiosClient.post<AuthResponseDto>('/auth/login', data).then((res) => res.data)
}

export function registerRequest(data: RegisterRequestDto) {
  return axiosClient.post<AuthResponseDto>('/auth/register', data).then((res) => res.data)
}

export function forgotPasswordRequest(data: ForgotPasswordRequestDto) {
  return axiosClient.post<void>('/auth/forgot-password', data).then(() => undefined)
}

export function resetPasswordRequest(data: ResetPasswordRequestDto) {
  return axiosClient.post<AuthResponseDto>('/auth/reset-password', data).then((res) => res.data)
}
