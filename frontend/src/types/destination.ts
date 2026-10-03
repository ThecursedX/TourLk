export type DestinationStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'TEMPORARILY_CLOSED'
  | 'INACTIVE'
  | 'ARCHIVED'

/** True when both coordinates are present, i.e. the destination can be placed on a map. */
export function hasCoordinates(d: { latitude?: number | null; longitude?: number | null }): boolean {
  return typeof d.latitude === 'number' && typeof d.longitude === 'number'
}

/** OpenStreetMap embed (iframe) URL centred on a marker; no API key or library needed. */
export function osmEmbedUrl(latitude: number, longitude: number): string {
  const delta = 0.02
  const bbox = [longitude - delta, latitude - delta, longitude + delta, latitude + delta].join(',')
  return `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${latitude},${longitude}`
}

export function osmLinkUrl(latitude: number, longitude: number): string {
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=14/${latitude}/${longitude}`
}

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
  openingHours?: string
  entryFee?: number | null
  visitorRules?: string
  latitude?: number | null
  longitude?: number | null
  /** On create only: save as DRAFT instead of publishing straight away. */
  saveAsDraft?: boolean
}

// Matches com.tourlk.dto.DestinationClosureRequestDto
export interface DestinationClosureRequestDto {
  reason: string
  // Both optional: from defaults to today, until to "until reopened manually".
  from?: string
  until?: string
}

// Matches com.tourlk.dto.ClosureSummaryDto
export interface ClosureSummaryDto {
  cancelledBookings: number
  refundedCount: number
  failedRefunds: number
  failedBookingIds: number[]
}

// Matches com.tourlk.dto.ClosureImpactDto
export interface ClosureImpactDto {
  affectedBookings: number
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
  openingHours: string | null
  entryFee: number | null
  visitorRules: string | null
  latitude: number | null
  longitude: number | null
  // Set only while TEMPORARILY_CLOSED.
  closureReason: string | null
  closureFrom: string | null
  closureUntil: string | null
  // Only on the response to closing a destination.
  closureSummary?: ClosureSummaryDto | null
  // Set only on nearby searches.
  distanceKm: number | null
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
  closureReason?: string | null
  closureFrom?: string | null
  closureUntil?: string | null
}

export interface DestinationBrowseParams {
  search?: string
  province?: Province
  /** "lat,lng"; results come back nearest first with distanceKm filled in. */
  nearby?: string
  radiusKm?: number
}
