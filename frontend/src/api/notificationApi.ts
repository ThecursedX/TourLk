import axiosClient from './axiosClient'
import type { NotificationResponseDto, UnreadCountResponseDto } from '../types/notification'

export function getMyNotifications() {
  return axiosClient.get<NotificationResponseDto[]>('/notifications/mine').then((res) => res.data)
}

export function getUnreadCount() {
  return axiosClient.get<UnreadCountResponseDto>('/notifications/unread-count').then((res) => res.data)
}

export function markNotificationRead(id: number) {
  return axiosClient.put<NotificationResponseDto>(`/notifications/${id}/read`).then((res) => res.data)
}

export function markAllNotificationsRead() {
  return axiosClient.put<void>('/notifications/read-all').then((res) => res.data)
}
