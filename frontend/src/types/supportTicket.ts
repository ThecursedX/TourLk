import type { Role } from './auth'

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export type TicketCategory = 'BOOKING' | 'PAYMENT' | 'ACCOUNT' | 'TECHNICAL' | 'OTHER'

export const TICKET_CATEGORIES: TicketCategory[] = ['BOOKING', 'PAYMENT', 'ACCOUNT', 'TECHNICAL', 'OTHER']

export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

// Matches com.tourlk.dto.SupportTicketRequestDto
export interface SupportTicketRequestDto {
  subject: string
  category: TicketCategory
  priority?: TicketPriority
  message: string
}

// Matches com.tourlk.dto.TicketReplyRequestDto
export interface TicketReplyRequestDto {
  message: string
}

// Matches com.tourlk.dto.SupportTicketResponseDto
export interface SupportTicketResponseDto {
  id: number
  subject: string
  category: TicketCategory
  priority: TicketPriority
  status: TicketStatus
  raisedById: number
  raisedByName: string
  assignedToId: number | null
  assignedToName: string | null
  createdAt: string
  updatedAt: string
}

// Matches com.tourlk.dto.TicketReplyResponseDto
export interface TicketReplyResponseDto {
  id: number
  authorId: number
  authorName: string
  authorRole: Role
  message: string
  createdAt: string
}

// Matches com.tourlk.dto.TicketDetailResponseDto
export interface TicketDetailResponseDto {
  ticket: SupportTicketResponseDto
  replies: TicketReplyResponseDto[]
}
