export type DestinationStatus = 'ACTIVE' | 'INACTIVE'

// Matches com.tourlk.enums.Province
export type Province =
  | 'WESTERN'
  | 'CENTRAL'
  | 'SOUTHERN'
  | 'NORTHERN'
  | 'EASTERN'
  | 'NORTH_WESTERN'
  | 'NORTH_CENTRAL'
  | 'UVA'
  | 'SABARAGAMUWA'

export const PROVINCES: Province[] = [
  'WESTERN',
  'CENTRAL',
  'SOUTHERN',
  'NORTHERN',
  'EASTERN',
  'NORTH_WESTERN',
  'NORTH_CENTRAL',
  'UVA',
  'SABARAGAMUWA',
]

export function formatProvince(province: Province | null | undefined): string {
  // Older destination rows saved before provinces existed can come back without one.
  if (!province) return ''
  return province
    .toLowerCase()
    .split('_')
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join(' ')
}

// Matches the response of GET /api/reference-data/provinces
export type ProvinceDistrictMap = Record<Province, string[]>

// Matches com.tourlk.dto.DestinationRequestDto
export interface DestinationRequestDto {
  name: string
  description?: string
  province: Province | ''
  district: string
  category: string
  bestTimeToVisit?: string
  imageUrls?: string[]
}

// Matches com.tourlk.dto.DestinationResponseDto
export interface DestinationResponseDto {
  id: number
  name: string
  description: string | null
  province: Province
  district: string
  category: string
  bestTimeToVisit: string | null
  imageUrls: string[]
  status: DestinationStatus
  // Populated on admin / detail responses; null on lightweight lists.
  activePackageCount: number | null
  activeAccommodationCount: number | null
  createdAt: string
}

/**
 * The trimmed destination shape nested inside TourPackage / Accommodation
 * responses (id + name + province/district + status, no listing counts).
 */
export interface DestinationSummary {
  id: number
  name: string
  province: Province
  district: string
  status: DestinationStatus
}

export interface DestinationBrowseParams {
  search?: string
  province?: Province
}
