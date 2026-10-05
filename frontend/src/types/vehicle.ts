export type VehicleStatus =
  | 'DRAFT'
  | 'PENDING_VERIFICATION'
  | 'AVAILABLE'
  | 'BOOKED'
  | 'UNDER_MAINTENANCE'
  | 'OUT_OF_SERVICE'
  | 'ARCHIVED'

/** Statuses a tourist can still send a hire request to (date overlaps are checked server-side). */
export const HIREABLE_VEHICLE_STATUSES: VehicleStatus[] = ['AVAILABLE', 'BOOKED']

export const MAX_VEHICLE_IMAGES = 10

export type VehicleType = 'CAR' | 'VAN' | 'MINIBUS' | 'BUS' | 'SUV' | 'TUKTUK'

export type VehicleHireStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'

export const VEHICLE_TYPES: VehicleType[] = ['CAR', 'VAN', 'MINIBUS', 'BUS', 'SUV', 'TUKTUK']

// Matches com.tourlk.dto.VehicleRequestDto
export interface VehicleRequestDto {
  vehicleType: VehicleType
  make: string
  model: string
  registrationNumber: string
  seatingCapacity: number
  pricePerDay: number
  airConditioned?: boolean
  facilities?: string[]
  imageUrls?: string[]
  driverName?: string
  driverPhone?: string
  insuranceExpiry?: string | null
  lastMaintenanceDate?: string | null
  nextMaintenanceDate?: string | null
}

// Matches com.tourlk.dto.VehicleResponseDto
export interface VehicleResponseDto {
  id: number
  vehicleType: VehicleType
  make: string
  model: string
  registrationNumber: string
  seatingCapacity: number
  pricePerDay: number
  status: VehicleStatus
  driverId: number
  driverName: string
  driverPhone: string | null
  airConditioned: boolean
  facilities: string[]
  imageUrls: string[]
  insuranceExpiry: string | null
  lastMaintenanceDate: string | null
  nextMaintenanceDate: string | null
}

// Matches com.tourlk.dto.VehicleHireRequestDto
export interface VehicleHireRequestDto {
  vehicleId: number
  startDate: string
  endDate: string
  pickupLocation: string
  notes?: string
}

// Matches com.tourlk.dto.VehicleSummaryDto
export interface VehicleSummaryDto {
  id: number
  vehicleType: VehicleType
  make: string
  model: string
  registrationNumber: string
  pricePerDay: number
  coverImageUrl: string | null
}

// Matches com.tourlk.dto.VehicleHireResponseDto
export interface VehicleHireResponseDto {
  id: number
  vehicle: VehicleSummaryDto
  touristId: number
  touristName: string
  startDate: string
  endDate: string
  pickupLocation: string
  notes: string | null
  totalPrice: number
  status: VehicleHireStatus
  /** Set when this is an add-on of a package booking — the booking pays for it. */
  bookingId: number | null
  packageTitle: string | null
  createdAt: string
}

/** Inclusive date range during which a vehicle is held by a PENDING or CONFIRMED hire. */
export interface BookedDateRange {
  startDate: string
  endDate: string
}
