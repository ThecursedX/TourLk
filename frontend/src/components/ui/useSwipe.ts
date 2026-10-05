import { useRef } from 'react'
import type { TouchEvent } from 'react'

/** Touch handlers that call onPrev / onNext after a mostly-horizontal swipe. */
export function useSwipe(onPrev: () => void, onNext: () => void, threshold = 40) {
  const start = useRef<{ x: number; y: number } | null>(null)

  return {
    onTouchStart: (event: TouchEvent) => {
      const touch = event.touches[0]
      start.current = { x: touch.clientX, y: touch.clientY }
    },
    onTouchEnd: (event: TouchEvent) => {
      const origin = start.current
      start.current = null
      if (!origin) return
      const touch = event.changedTouches[0]
      const dx = touch.clientX - origin.x
      const dy = touch.clientY - origin.y
      if (Math.abs(dx) < threshold || Math.abs(dx) < Math.abs(dy)) return
      if (dx > 0) onPrev()
      else onNext()
    },
  }
}
