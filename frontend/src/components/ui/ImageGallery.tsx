import { useEffect, useState } from 'react'

interface ImageGalleryProps {
  urls: string[]
  alt: string
}

/** Cover image with a thumbnail strip and a click-to-enlarge lightbox; renders nothing when there are no images. */
export default function ImageGallery({ urls, alt }: ImageGalleryProps) {
  const [selected, setSelected] = useState(0)
  const [broken, setBroken] = useState<Set<string>>(new Set())
  const [lightboxOpen, setLightboxOpen] = useState(false)

  const visible = urls.filter((url) => !broken.has(url))
  const count = visible.length
  const currentIndex = Math.min(selected, Math.max(count - 1, 0))
  const current = visible[currentIndex]
  const markBroken = (url: string) => setBroken((prev) => new Set(prev).add(url))

  const showPrev = () => setSelected((currentIndex - 1 + count) % count)
  const showNext = () => setSelected((currentIndex + 1) % count)

  // Close the lightbox if the last image disappears (all broken).
  useEffect(() => {
    if (count === 0) setLightboxOpen(false)
  }, [count])

  useEffect(() => {
    if (!lightboxOpen) return

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setLightboxOpen(false)
      } else if (count > 1 && event.key === 'ArrowLeft') {
        setSelected((currentIndex - 1 + count) % count)
      } else if (count > 1 && event.key === 'ArrowRight') {
        setSelected((currentIndex + 1) % count)
      }
    }
    window.addEventListener('keydown', onKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [lightboxOpen, count, currentIndex])

  if (count === 0) return null

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        onClick={() => setLightboxOpen(true)}
        className="flex h-64 w-full cursor-zoom-in items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-100"
        aria-label="Enlarge image"
      >
        <img
          src={current}
          alt={alt}
          className="h-full w-full object-contain"
          onError={() => markBroken(current)}
        />
      </button>
      {count > 1 && (
        <div className="flex flex-wrap gap-2">
          {visible.map((url, index) => (
            <button
              key={url}
              type="button"
              onClick={() => setSelected(index)}
              className={`overflow-hidden rounded-lg border-2 ${
                url === current ? 'border-blue-500' : 'border-transparent'
              }`}
              aria-label={`Show image ${index + 1}`}
            >
              <img
                src={url}
                alt=""
                className="h-14 w-14 object-cover"
                onError={() => markBroken(url)}
              />
            </button>
          ))}
        </div>
      )}

      {lightboxOpen && (
        <div
          className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="Image viewer"
          onClick={() => setLightboxOpen(false)}
        >
          <button
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
