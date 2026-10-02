import type { DestinationSummary } from './destination'

export type PackageStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'

// Matches com.tourlk.enums.BudgetTier — price per day, thresholds set on the backend.
export type BudgetTier = 'BUDGET' | 'STANDARD' | 'LUXURY'

// Query-string values of com.tourlk.enums.PackageSort
export type PackageSort = 'price_asc' | 'price_desc' | 'duration' | 'rating' | 'newest'

// Matches com.tourlk.dto.ItineraryDayRequestDto
export interface ItineraryDayRequestDto {
  dayNumber: number
  title: string
  description?: string
  placesToVisit?: string[]
}

// Matches com.tourlk.dto.ItineraryDayResponseDto
export interface ItineraryDayResponseDto {
  id: number
  dayNumber: number
  title: string
  description: string | null
  placesToVisit: string[]
}

// Matches com.tourlk.dto.TourPackageRequestDto
export interface TourPackageRequestDto {
  title: string
  description: string
  destinationId: number | ''
  durationDays: number
  price: number
  maxCapacity: number
  itineraryDays?: ItineraryDayRequestDto[]
  inclusions?: string[]
  exclusions?: string[]
  imageUrls?: string[]
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
  /** The admin's reason from the last rejection; cleared when the package is resubmitted. */
  rejectionReason: string | null
  itineraryDays: ItineraryDayResponseDto[]
  inclusions: string[]
  exclusions: string[]
  imageUrls: string[]
  /** True once the package has any departure; bookings must then pick one. */
  hasDepartures: boolean
  /** Rounded to one decimal; 0 when reviewCount is 0. */
  averageRating: number
  reviewCount: number
  budgetTier: BudgetTier
}

// Matches com.tourlk.dto.PackageDepartureRequestDto
export interface PackageDepartureRequestDto {
  departureDate: string
  /** Defaults to the package's max capacity when omitted. */
  seatsTotal?: number
}

// Matches com.tourlk.dto.PackageDepartureResponseDto
export interface PackageDepartureResponseDto {
  id: number
  departureDate: string
  seatsTotal: number
  seatsLeft: number
}

export interface TourPackageSearchParams {
  destinationId?: number
  minPrice?: number
  maxPrice?: number
  /** Case-insensitive text in title or description. */
  q?: string
  minDays?: number
  maxDays?: number
  budgetTier?: BudgetTier
  /** "YYYY-MM-DD"; keeps packages departing on/after it (packages without departures always match). */
  travelDate?: string
  sort?: PackageSort
}
