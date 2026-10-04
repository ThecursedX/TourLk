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
  licenceDocumentUploaded: boolean
  licenceExpired: boolean
  // Days until licenceExpiry (negative once expired); null when there is no expiry date
  licenceDaysUntilExpiry: number | null
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

// Sent as multipart/form-data (see com.tourlk.dto.LicenceSubmitRequestDto + the `file` part)
export interface LicenceSubmitRequestDto {
  licenceNumber: string
  licenceExpiry: string
  file: File
}

export const LICENCE_ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
export const LICENCE_MAX_BYTES = 5 * 1024 * 1024
