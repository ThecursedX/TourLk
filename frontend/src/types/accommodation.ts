import type { DestinationSummary } from './destination'

export type AccommodationStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'ACTIVE'
  | 'FULLY_BOOKED'
  | 'TEMPORARILY_UNAVAILABLE'
  | 'INACTIVE'
  | 'ARCHIVED'

/** Approved listings that stay publicly visible (and editable by the owner). */
export function isLiveAccommodation(status: AccommodationStatus): boolean {
  return status === 'ACTIVE' || status === 'FULLY_BOOKED' || status === 'TEMPORARILY_UNAVAILABLE'
}

export const MAX_ACCOMMODATION_IMAGES = 10

export type RoomReservationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED'

// Matches com.tourlk.dto.AccommodationRequestDto
export interface AccommodationRequestDto {
  name: string
  description: string
  locationId: number | ''
  starRating?: number
  address?: string
  latitude?: number | null
  longitude?: number | null
  facilities?: string[]
  policies?: string
  imageUrls?: string[]
}

// Matches com.tourlk.dto.RoomRequestDto
export interface RoomRequestDto {
  roomType: string
  pricePerNight: number
  totalRooms: number
  maxOccupancy: number
  facilities?: string[]
  imageUrls?: string[]
}

// Matches com.tourlk.dto.RoomResponseDto
export interface RoomResponseDto {
  id: number
  accommodationId: number
  roomType: string
  pricePerNight: number
  totalRooms: number
  maxOccupancy: number
  facilities: string[]
  imageUrls: string[]
}

// Matches com.tourlk.dto.AccommodationResponseDto
export interface AccommodationResponseDto {
  id: number
  name: string
  description: string
  location: DestinationSummary
  starRating: number | null
  address: string | null
  latitude: number | null
  longitude: number | null
  facilities: string[]
  policies: string | null
  imageUrls: string[]
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
  /** Set when this is an add-on of a package booking — the booking pays for it. */
  bookingId: number | null
  packageTitle: string | null
  createdAt: string
}
