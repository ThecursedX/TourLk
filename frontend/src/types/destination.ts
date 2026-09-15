export type DestinationStatus = 'ACTIVE' | 'INACTIVE'

// Matches com.tourlk.dto.DestinationRequestDto
export interface DestinationRequestDto {
  name: string
  region: string
  district?: string
  category?: string
  bestTimeToVisit?: string
  imageUrl?: string
  description?: string
}

// Matches com.tourlk.dto.DestinationResponseDto
export interface DestinationResponseDto {
  id: number
  name: string
  region: string
  district: string | null
  category: string | null
  bestTimeToVisit: string | null
  imageUrl: string | null
  description: string | null
  status: DestinationStatus
  // Populated on admin / detail responses; null on lightweight lists.
  activePackageCount: number | null
  activeAccommodationCount: number | null
  createdAt: string
}

/**
 * The trimmed destination shape nested inside TourPackage / Accommodation
 * responses (id + name + region + status, no listing counts).
 */
export interface DestinationSummary {
  id: number
  name: string
  region: string
  status: DestinationStatus
}

export interface DestinationBrowseParams {
  search?: string
  region?: string
}