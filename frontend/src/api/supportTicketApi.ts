import axiosClient from './axiosClient'
import type {
  SupportTicketRequestDto,
  SupportTicketResponseDto,
  TicketDetailResponseDto,
  TicketReplyRequestDto,
  TicketReplyResponseDto,
  TicketStatus,
} from '../types/supportTicket'

/**
 * The JSON body goes in a `data` part and each file in a `files` part; with no
 * files a plain JSON request is sent, as before.
 */
function asMultipart(data: object, files: File[]) {
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  files.forEach((file) => form.append('files', file))
  return form
}

export function createTicket(data: SupportTicketRequestDto, files: File[] = []) {
  return axiosClient
    .post<SupportTicketResponseDto>('/tickets', files.length > 0 ? asMultipart(data, files) : data)
    .then((res) => res.data)
}

export function addReply(ticketId: number, data: TicketReplyRequestDto, files: File[] = []) {
  return axiosClient
    .post<TicketReplyResponseDto>(
      `/tickets/${ticketId}/replies`,
      files.length > 0 ? asMultipart(data, files) : data,
    )
    .then((res) => res.data)
}

export function withdrawTicket(id: number) {
  return axiosClient.put<SupportTicketResponseDto>(`/tickets/${id}/withdraw`).then((res) => res.data)
}

/** Downloads through axios so the auth header is sent, then hands the blob to the browser. */
export async function downloadAttachment(ticketId: number, attachmentId: number, fileName: string) {
  const res = await axiosClient.get<Blob>(`/tickets/${ticketId}/attachments/${attachmentId}`, {
    responseType: 'blob',
  })
  const url = URL.createObjectURL(res.data)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
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
