import type { Role } from './auth'

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED'

// Matches com.tourlk.enums.VerificationStatus
export type VerificationStatus = 'NOT_SUBMITTED' | 'PENDING' | 'VERIFIED' | 'REJECTED'

// Matches com.tourlk.dto.UserResponseDto
export interface UserResponseDto {
  id: number
  name: string
  email: string
  phone: string | null
  role: Role
  status: UserStatus
  createdAt: string
  verificationStatus: VerificationStatus
  licenceNumber: string | null
  licenceExpiry: string | null
  licenceDocumentUrl: string | null
  licenceRejectionReason: string | null
  licenceVerifiedAt: string | null
}

// Matches com.tourlk.dto.UpdateProfileRequestDto
export interface UpdateProfileRequestDto {
  name: string
  email: string
  phone?: string
}

// Matches com.tourlk.dto.ChangePasswordRequestDto
export interface ChangePasswordRequestDto {
  currentPassword: string
  newPassword: string
}

// Matches com.tourlk.dto.LicenceSubmitRequestDto
export interface LicenceSubmitRequestDto {
  licenceNumber: string
  licenceExpiry: string
  licenceDocumentUrl: string
}
