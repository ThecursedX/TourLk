import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyNotifications, getUnreadCount, markNotificationRead } from '../../api/notificationApi'
import type { NotificationResponseDto } from '../../types/notification'

const POLL_INTERVAL_MS = 60_000

/**
 * Bell icon with an unread badge, polled every 60s so the count stays
 * fresh without the user having to refresh. Opening the dropdown loads
 * the latest notifications on demand rather than polling the full list.
 */
export default function NotificationBell() {
  const [unreadCount, setUnreadCount] = useState(0)
  const [open, setOpen] = useState(false)
  const [notifications, setNotifications] = useState<NotificationResponseDto[]>([])
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const refreshCount = () => {
      getUnreadCount()
        .then((res) => setUnreadCount(res.count))
        .catch(() => {})
    }
    refreshCount()
    const interval = setInterval(refreshCount, POLL_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const toggleOpen = () => {
    const next = !open
    setOpen(next)
    if (next) {
      setLoading(true)
      getMyNotifications()
        .then(setNotifications)
        .catch(() => {})
        .finally(() => setLoading(false))
    }
  }

  const handleNotificationClick = (notification: NotificationResponseDto) => {
    if (!notification.read) {
      markNotificationRead(notification.id)
        .then(() => {
          setNotifications((prev) =>
            prev.map((n) => (n.id === notification.id ? { ...n, read: true } : n)),
          )
          setUnreadCount((prev) => Math.max(0, prev - 1))
        })
        .catch(() => {})
    }
    setOpen(false)
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={toggleOpen}
        aria-label="Notifications"
        className="relative flex h-9 w-9 items-center justify-center rounded-full text-slate-600 transition-colors hover:bg-slate-100 hover:text-blue-600"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <path
            d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-12 z-50 w-80 max-w-[90vw] rounded-2xl border border-white/50 bg-white/95 p-2 shadow-soft glass-panel">
          <div className="flex items-center justify-between px-2 py-1.5">
            <span className="text-sm font-semibold text-slate-900">Notifications</span>
            <Link to="/notifications" onClick={() => setOpen(false)} className="text-xs font-medium text-blue-600 hover:text-blue-700">
              View all
            </Link>
          </div>

          <div className="max-h-96 overflow-y-auto">
            {loading && <p className="px-2 py-4 text-center text-sm text-slate-500">Loading...</p>}
            {!loading && notifications.length === 0 && (
              <p className="px-2 py-4 text-center text-sm text-slate-500">You're all caught up.</p>
            )}
            {!loading &&
              notifications.map((notification) => {
                const content = (
                  <div
                    className={`rounded-xl px-2 py-2 text-sm transition-colors hover:bg-slate-100 ${
                      notification.read ? 'text-slate-500' : 'bg-blue-50/70 text-slate-900'
                    }`}
                  >
                    <p className="font-medium">{notification.title}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{notification.message}</p>
                  </div>
                )
                return (
                  <Link
                    key={notification.id}
                    to={notification.linkUrl ?? '/notifications'}
                    onClick={() => handleNotificationClick(notification)}
                    className="block"
                  >
                    {content}
                  </Link>
                )
              })}
          </div>
        </div>
      )}
    </div>
  )
}
