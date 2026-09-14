import type { DestinationSummary } from './destination'

export type AccommodationStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'

export type RoomReservationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'

// Matches com.tourlk.dto.AccommodationRequestDto
export interface AccommodationRequestDto {
  name: string
  description: string
  locationId: number | ''
  starRating?: number
}

// Matches com.tourlk.dto.RoomRequestDto
export interface RoomRequestDto {
  roomType: string
  pricePerNight: number
  totalRooms: number
  maxOccupancy: number
}

// Matches com.tourlk.dto.RoomResponseDto
export interface RoomResponseDto {
  id: number
  accommodationId: number
  roomType: string
  pricePerNight: number
  totalRooms: number
  maxOccupancy: number
}

// Matches com.tourlk.dto.AccommodationResponseDto
export interface AccommodationResponseDto {
  id: number
  name: string
  description: string
  location: DestinationSummary
  starRating: number | null
  status: AccommodationStatus
  ownerId: number
  ownerName: string
  rooms: RoomResponseDto[]
  createdAt: string
}

// Matches com.tourlk.dto.RoomReservationRequestDto
export interface RoomReservationRequestDto {
  roomId: number
  checkInDate: string
  checkOutDate: string
  numberOfRooms: number
}

// Matches com.tourlk.dto.RoomSummaryDto
export interface RoomSummaryDto {
  id: number
  roomType: string
  pricePerNight: number
  accommodationId: number
  accommodationName: string
  accommodationLocation: string
}

// Matches com.tourlk.dto.RoomReservationResponseDto
export interface RoomReservationResponseDto {
  id: number
  room: RoomSummaryDto
  touristId: number
  touristName: string
  checkInDate: string
  checkOutDate: string
  numberOfRooms: number
  status: RoomReservationStatus
  createdAt: string
}
