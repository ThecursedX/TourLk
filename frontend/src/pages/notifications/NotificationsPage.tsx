import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyNotifications, markAllNotificationsRead, markNotificationRead } from '../../api/notificationApi'
import Button from '../../components/ui/Button'
import type { NotificationResponseDto } from '../../types/notification'

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationResponseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [markingAll, setMarkingAll] = useState(false)

  useEffect(() => {
    setLoading(true)
    setError(null)
    getMyNotifications()
      .then(setNotifications)
      .catch(() => setError('Could not load your notifications. Please try again later.'))
      .finally(() => setLoading(false))
  }, [])

  const handleMarkRead = async (id: number) => {
    setBusyId(id)
    try {
      const updated = await markNotificationRead(id)
      setNotifications((prev) => prev.map((n) => (n.id === id ? updated : n)))
    } catch {
      setError('Could not update that notification. Please try again.')
    } finally {
      setBusyId(null)
    }
  }

  const handleMarkAllRead = async () => {
    setMarkingAll(true)
    try {
      await markAllNotificationsRead()
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
    } catch {
      setError('Could not mark all as read. Please try again.')
    } finally {
      setMarkingAll(false)
    }
  }

  const hasUnread = notifications.some((n) => !n.read)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Notifications</h1>
          <p className="mt-1 text-slate-600">Updates on your bookings, packages, payments and more.</p>
        </div>
        <Button variant="secondary" disabled={!hasUnread || markingAll} onClick={handleMarkAllRead}>
          Mark all as read
        </Button>
      </div>

      {loading && <p className="text-slate-600">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {!loading && !error && notifications.length === 0 && (
        <p className="text-slate-600">You have no notifications yet.</p>
      )}

      <div className="flex flex-col gap-3">
        {notifications.map((notification) => (
          <div
            key={notification.id}
            className={`flex items-start justify-between gap-4 rounded-2xl border p-4 shadow-soft ${
              notification.read ? 'border-slate-200 bg-white' : 'border-blue-200 bg-blue-50/60'
            }`}
          >
            <div className="flex flex-col gap-1">
              <p className="font-semibold text-slate-900">{notification.title}</p>
              <p className="text-sm text-slate-600">{notification.message}</p>
              <p className="text-xs text-slate-400">{new Date(notification.createdAt).toLocaleString()}</p>
              {notification.linkUrl && (
                <Link
                  to={notification.linkUrl}
                  className="mt-1 text-sm font-medium text-blue-600 hover:text-blue-700"
                  onClick={() => !notification.read && handleMarkRead(notification.id)}
                >
                  View details
                </Link>
              )}
            </div>
            {!notification.read && (
              <Button
                variant="secondary"
                disabled={busyId === notification.id}
                onClick={() => handleMarkRead(notification.id)}
              >
                Mark as read
              </Button>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
