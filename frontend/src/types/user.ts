import type { Role } from './auth'

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED'

// Matches com.tourlk.dto.UserResponseDto
export interface UserResponseDto {
  id: number
  name: string
  email: string
  phone: string | null
  role: Role
  status: UserStatus
  createdAt: string
}

// Matches com.tourlk.dto.UpdateProfileRequestDto
export interface UpdateProfileRequestDto {
  name: string
  email: string
  phone?: string
}
