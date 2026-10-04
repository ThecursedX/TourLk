import type { Role } from './auth'

export type TicketStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'WAITING_FOR_USER'
  | 'RESOLVED'
  | 'CLOSED'
  | 'WITHDRAWN'

/** Statuses in which a ticket is still being worked on (can be withdrawn or resolved). */
export const ACTIVE_TICKET_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'WAITING_FOR_USER']

// Attachment rules, mirroring TicketAttachmentStorage on the server.
export const MAX_TICKET_FILES = 3
export const MAX_TICKET_FILE_BYTES = 5 * 1024 * 1024
export const ALLOWED_TICKET_FILE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'application/pdf']

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

// Matches com.tourlk.dto.TicketAttachmentResponseDto
export interface TicketAttachmentResponseDto {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
}

// Matches com.tourlk.dto.TicketReplyResponseDto
export interface TicketReplyResponseDto {
  id: number
  authorId: number
  authorName: string
  authorRole: Role
  message: string
  attachments: TicketAttachmentResponseDto[]
  createdAt: string
}

// Matches com.tourlk.dto.TicketDetailResponseDto
export interface TicketDetailResponseDto {
  ticket: SupportTicketResponseDto
  replies: TicketReplyResponseDto[]
}
