import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import ImagePlaceholder from './ImagePlaceholder'
import { useSwipe } from './useSwipe'

interface ImageGalleryProps {
  urls: string[]
  alt: string
}

const arrowClass =
  'absolute top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-black/50 text-2xl leading-none text-white hover:bg-black/70'

/** Cover image with a scrollable thumbnail strip and a click-to-enlarge lightbox; shows a placeholder when nothing loads. */
export default function ImageGallery({ urls, alt }: ImageGalleryProps) {
  const signature = urls.join('\n')
  const [state, setState] = useState({ signature, selected: 0, broken: [] as string[] })
  // Different image list: reset the selection and the broken-image memory.
  if (state.signature !== signature) setState({ signature, selected: 0, broken: [] })

  const [lightboxOpen, setLightboxOpen] = useState(false)
  const thumbsRef = useRef<HTMLDivElement>(null)
  const dialogRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  const visible = urls.filter((url) => url && !state.broken.includes(url))
  const count = visible.length
  const currentIndex = Math.min(state.selected, Math.max(count - 1, 0))
  const current = visible[currentIndex]
  const markBroken = (url: string) =>
    setState((prev) => (prev.broken.includes(url) ? prev : { ...prev, broken: [...prev.broken, url] }))

  const select = (index: number) => setState((prev) => ({ ...prev, selected: index }))
  const showPrev = () => {
    if (count > 1) select((currentIndex - 1 + count) % count)
  }
  const showNext = () => {
    if (count > 1) select((currentIndex + 1) % count)
  }
  const pageSwipe = useSwipe(showPrev, showNext)
  const lightboxSwipe = useSwipe(showPrev, showNext)

  // Close the lightbox if the last image disappears (all broken).
  useEffect(() => {
    if (count === 0) setLightboxOpen(false)
  }, [count])

  // Keep the active thumbnail visible (scrolls the strip only, never the page).
  useEffect(() => {
    const strip = thumbsRef.current
    const thumb = strip?.children[currentIndex] as HTMLElement | undefined
    if (!strip || !thumb) return
    if (thumb.offsetLeft < strip.scrollLeft) {
      strip.scrollTo({ left: thumb.offsetLeft - 8, behavior: 'smooth' })
    } else if (thumb.offsetLeft + thumb.offsetWidth > strip.scrollLeft + strip.clientWidth) {
      strip.scrollTo({ left: thumb.offsetLeft + thumb.offsetWidth - strip.clientWidth + 8, behavior: 'smooth' })
    }
  }, [currentIndex, count])

  useEffect(() => {
    if (!lightboxOpen) return

    const opener = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setLightboxOpen(false)
      } else if (count > 1 && event.key === 'ArrowLeft') {
        setState((prev) => ({ ...prev, selected: (currentIndex - 1 + count) % count }))
      } else if (count > 1 && event.key === 'ArrowRight') {
        setState((prev) => ({ ...prev, selected: (currentIndex + 1) % count }))
      }
    }
    window.addEventListener('keydown', onKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
      opener?.focus?.()
    }
  }, [lightboxOpen, count, currentIndex])

  // Keep Tab inside the dialog.
  const trapFocus = (event: ReactKeyboardEvent) => {
    if (event.key !== 'Tab') return
    const focusable = dialogRef.current?.querySelectorAll<HTMLElement>('button:not([disabled])')
    if (!focusable || focusable.length === 0) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  if (count === 0) return <ImagePlaceholder />

  return (
    <div className="mx-auto flex w-full min-w-0 flex-col gap-2">
      <div
        className="group relative aspect-[16/10] w-full overflow-hidden rounded-xl border border-slate-200 bg-slate-100"
        {...pageSwipe}
      >
        <button
          type="button"
          onClick={() => setLightboxOpen(true)}
          className="block h-full w-full cursor-zoom-in"
          aria-label="Enlarge image"
        >
          <img
            key={`${currentIndex}-${current}`}
            src={current}
            alt={alt}
            className="h-full w-full object-cover"
            onError={() => markBroken(current)}
          />
        </button>
        <span className="pointer-events-none absolute bottom-2 left-2 rounded-full bg-black/60 px-2.5 py-0.5 text-xs text-white">
          Click to enlarge
        </span>
        {count > 1 && (
          <>
            <span className="pointer-events-none absolute right-2 top-2 rounded-full bg-black/60 px-2.5 py-0.5 text-xs font-medium text-white">
              {currentIndex + 1} / {count}
            </span>
            <button type="button" onClick={showPrev} className={`${arrowClass} left-2`} aria-label="Previous image">
              &#8249;
            </button>
            <button type="button" onClick={showNext} className={`${arrowClass} right-2`} aria-label="Next image">
              &#8250;
            </button>
          </>
        )}
      </div>

      {count > 1 && (
        <div ref={thumbsRef} className="relative mx-auto flex w-fit max-w-full gap-2 overflow-x-auto p-1">
          {visible.map((url, index) => (
            <button
              key={`${index}-${url}`}
              type="button"
              onClick={() => select(index)}
              className={`h-16 w-24 shrink-0 overflow-hidden rounded-lg ${
                index === currentIndex ? 'ring-2 ring-blue-500 ring-offset-1' : 'opacity-80 hover:opacity-100'
              }`}
              aria-label={`Show image ${index + 1}`}
              aria-current={index === currentIndex}
            >
              <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" onError={() => markBroken(url)} />
            </button>
          ))}
        </div>
      )}

      {lightboxOpen && (
        <div
          ref={dialogRef}
          className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="Image viewer"
          onClick={() => setLightboxOpen(false)}
          onKeyDown={trapFocus}
          {...lightboxSwipe}
        >
          <button
            ref={closeRef}
            type="button"
            onClick={() => setLightboxOpen(false)}
            className="absolute right-4 top-4 flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-2xl leading-none text-white hover:bg-white/20"
            aria-label="Close image viewer"
          >
            &times;
          </button>

          {count > 1 && (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation()
                showPrev()
              }}
              className="absolute left-4 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-2xl leading-none text-white hover:bg-white/20"
              aria-label="Previous image"
            >
              &#8249;
            </button>
          )}

          <img
            src={current}
            alt={alt}
            className="max-h-[90vh] max-w-[90vw] object-contain"
            onClick={(event) => event.stopPropagation()}
            onError={() => markBroken(current)}
          />

          {count > 1 && (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation()
                showNext()
              }}
              className="absolute right-4 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-2xl leading-none text-white hover:bg-white/20"
              aria-label="Next image"
            >
              &#8250;
            </button>
          )}

          <span
            className="absolute bottom-4 left-1/2 -translate-x-1/2 rounded-full bg-black/60 px-3 py-1 text-sm text-white"
            aria-live="polite"
          >
            {currentIndex + 1} / {count}
          </span>
        </div>
      )}
    </div>
  )
}
