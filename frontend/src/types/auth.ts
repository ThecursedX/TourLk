export type Role = 'TOURIST' | 'GUIDE' | 'DRIVER' | 'HOTEL_PARTNER' | 'ADMIN'

export const ROLES: Role[] = ['TOURIST', 'GUIDE', 'DRIVER', 'HOTEL_PARTNER', 'ADMIN']

// Matches com.tourlk.dto.RegisterRequestDto
export interface RegisterRequestDto {
  name: string
  email: string
  password: string
  phone?: string
  role: Role
}

// Matches com.tourlk.dto.LoginRequestDto
export interface LoginRequestDto {
  email: string
  password: string
}

// Matches com.tourlk.dto.AuthResponseDto
export interface AuthResponseDto {
  userId: number
  name: string
  email: string
  role: Role
  token: string
  tokenType: string
}

export interface User {
  userId: number
  name: string
  email: string
  role: Role
}

// Matches com.tourlk.exception.ErrorResponse
export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}
