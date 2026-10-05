import { useState } from 'react'
import type { MouseEvent } from 'react'
import ImagePlaceholder from './ImagePlaceholder'
import { useSwipe } from './useSwipe'

interface CardImageProps {
  urls: string[]
  alt: string
  className?: string
}

const arrowClass =
  'absolute top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-black/50 text-xl leading-none text-white opacity-0 transition-opacity hover:bg-black/70 focus-visible:opacity-100 group-hover:opacity-100 [@media(hover:none)]:opacity-100'

/** Fixed-ratio cover image for cards, with a placeholder and prev/next arrows when there are several photos. */
export default function CardImage({ urls, alt, className = '' }: CardImageProps) {
  const signature = urls.join('\n')
  const [state, setState] = useState({ signature, index: 0, broken: [] as string[] })
  // Different image list (e.g. after an edit): start over.
  if (state.signature !== signature) setState({ signature, index: 0, broken: [] })

  const visible = urls.filter((url) => url && !state.broken.includes(url))
  const count = visible.length
  const index = Math.min(state.index, Math.max(count - 1, 0))

  const go = (next: number) => setState((prev) => ({ ...prev, index: (next + count) % count }))
  const swipe = useSwipe(
    () => count > 1 && go(index - 1),
    () => count > 1 && go(index + 1),
  )

  if (count === 0) return <ImagePlaceholder className={className} />

  const current = visible[index]
  const step = (delta: number) => (event: MouseEvent) => {
    event.preventDefault()
    event.stopPropagation()
    go(index + delta)
  }

  return (
    <div
      className={`group relative aspect-[16/10] w-full overflow-hidden rounded-xl border border-slate-200 bg-slate-100 ${className}`}
      {...swipe}
    >
      <img
        key={`${index}-${current}`}
        src={current}
        alt={alt}
        loading="lazy"
        className="h-full w-full object-cover"
        onError={() => setState((prev) => ({ ...prev, broken: [...prev.broken, current] }))}
      />
      {count > 1 && (
        <>
          <span className="absolute right-2 top-2 rounded-full bg-black/60 px-2 py-0.5 text-xs font-medium text-white">
            {index + 1} / {count}
          </span>
          <button type="button" onClick={step(-1)} className={`${arrowClass} left-2`} aria-label="Previous photo">
            &#8249;
          </button>
          <button type="button" onClick={step(1)} className={`${arrowClass} right-2`} aria-label="Next photo">
            &#8250;
          </button>
        </>
      )}
    </div>
  )
}
