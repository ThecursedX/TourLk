import { useState } from 'react'

interface ImageGalleryProps {
  urls: string[]
  alt: string
}

/** Cover image with a thumbnail strip; renders nothing when there are no images. */
export default function ImageGallery({ urls, alt }: ImageGalleryProps) {
  const [selected, setSelected] = useState(0)
  const [broken, setBroken] = useState<Set<string>>(new Set())

  const visible = urls.filter((url) => !broken.has(url))
  if (visible.length === 0) return null

  const current = visible[Math.min(selected, visible.length - 1)]
  const markBroken = (url: string) => setBroken((prev) => new Set(prev).add(url))

  return (
    <div className="flex flex-col gap-2">
      <img
        src={current}
        alt={alt}
        className="h-64 w-full rounded-xl border border-slate-200 object-cover"
        onError={() => markBroken(current)}
      />
      {visible.length > 1 && (
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
    </div>
  )
}
