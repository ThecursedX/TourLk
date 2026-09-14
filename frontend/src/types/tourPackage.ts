import type { DestinationSummary } from './destination'

export type PackageStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'

// Matches com.tourlk.dto.TourPackageRequestDto
export interface TourPackageRequestDto {
  title: string
  description: string
  destinationId: number | ''
  durationDays: number
  price: number
  maxCapacity: number
}

// Matches com.tourlk.dto.TourPackageResponseDto
export interface TourPackageResponseDto {
  id: number
  title: string
  description: string
  destination: DestinationSummary
  durationDays: number
  price: number
  maxCapacity: number
  status: PackageStatus
  createdById: number
  createdByName: string
  createdAt: string
}

export interface TourPackageSearchParams {
  destinationId?: number
  minPrice?: number
  maxPrice?: number
}
