export type VehicleStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'

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
  createdAt: string
}
