import axiosClient from './axiosClient'
import type {
  SupportTicketRequestDto,
  SupportTicketResponseDto,
  TicketDetailResponseDto,
  TicketReplyRequestDto,
  TicketReplyResponseDto,
  TicketStatus,
} from '../types/supportTicket'

export function createTicket(data: SupportTicketRequestDto) {
  return axiosClient.post<SupportTicketResponseDto>('/tickets', data).then((res) => res.data)
}

export function addReply(ticketId: number, data: TicketReplyRequestDto) {
  return axiosClient.post<TicketReplyResponseDto>(`/tickets/${ticketId}/replies`, data).then((res) => res.data)
}

export function assignTicket(id: number) {
  return axiosClient.put<SupportTicketResponseDto>(`/tickets/${id}/assign`).then((res) => res.data)
}

export function resolveTicket(id: number) {
  return axiosClient.put<SupportTicketResponseDto>(`/tickets/${id}/resolve`).then((res) => res.data)
}

export function closeTicket(id: number) {
  return axiosClient.put<SupportTicketResponseDto>(`/tickets/${id}/close`).then((res) => res.data)
}

export function reopenTicket(id: number) {
  return axiosClient.put<SupportTicketResponseDto>(`/tickets/${id}/reopen`).then((res) => res.data)
}

export function getMyTickets() {
  return axiosClient.get<SupportTicketResponseDto[]>('/tickets/mine').then((res) => res.data)
}

export function getTicketById(id: number) {
  return axiosClient.get<TicketDetailResponseDto>(`/tickets/${id}`).then((res) => res.data)
}

export function getAllTickets(status?: TicketStatus) {
  return axiosClient
    .get<SupportTicketResponseDto[]>('/tickets', { params: status ? { status } : undefined })
    .then((res) => res.data)
}

export function getUnassignedTickets() {
  return axiosClient.get<SupportTicketResponseDto[]>('/tickets/unassigned').then((res) => res.data)
}
