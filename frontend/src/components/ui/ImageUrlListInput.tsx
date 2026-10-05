import { useState } from 'react'
import Button from './Button'
import Input from './Input'

interface ImageUrlListInputProps {
  label?: string
  values: string[]
  onChange: (values: string[]) => void
  max?: number
  error?: string
}

const isHttpUrl = (url: string) => /^https?:\/\/\S+/i.test(url.trim())

/** True when any non-blank row isn't an http(s) URL, so forms can block submit. */
export function hasInvalidImageUrl(urls: string[] | undefined): boolean {
  return (urls ?? []).some((url) => url.trim() !== '' && !isHttpUrl(url))
}

/**
 * Add/remove/reorder list of image URLs with a preview per row. Same editor
 * the package form uses, shared so vehicles, properties and rooms all cap
 * at the same limit. The first row is the cover image. Blank rows are kept
 * while editing; callers should drop them on submit (see {@link cleanImageUrls}).
 */
export default function ImageUrlListInput({
  label = 'Images (optional)',
  values,
  onChange,
  max = 10,
  error,
}: ImageUrlListInputProps) {
  // URLs the browser failed to load. Keyed by URL text, so editing a row
  // naturally clears its failure (the new text isn't in the set).
  const [failed, setFailed] = useState<Set<string>>(new Set())

  const update = (index: number, url: string) => {
    const next = [...values]
    next[index] = url
    onChange(next)
  }

  const move = (from: number, to: number) => {
    if (to < 0 || to >= values.length) return
    const next = [...values]
    const [item] = next.splice(from, 1)
    next.splice(to, 0, item)
    onChange(next)
  }

  const counts = new Map<string, number>()
  values.forEach((url) => {
    const key = url.trim()
    if (key) counts.set(key, (counts.get(key) ?? 0) + 1)
  })

  const smallButton = '!px-2 !py-1 text-xs'

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-medium text-slate-700">{label}</span>
        <span className="text-xs text-slate-500">
          {values.length} / {max}
        </span>
      </div>
      {values.map((url, index) => {
        const trimmed = url.trim()
        const invalid = trimmed !== '' && !isHttpUrl(trimmed)
        const duplicate = trimmed !== '' && (counts.get(trimmed) ?? 0) > 1
        const loadFailed = trimmed !== '' && !invalid && failed.has(trimmed)

        return (
          <div key={index} className="flex min-w-0 flex-col gap-1">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              {trimmed && !invalid ? (
                <img
                  src={trimmed}
                  alt=""
                  className={`h-16 w-24 shrink-0 rounded-lg border object-cover ${
                    loadFailed ? 'border-red-300 bg-red-50 opacity-40' : 'border-slate-200'
                  }`}
                  onError={() => setFailed((prev) => new Set(prev).add(trimmed))}
                />
              ) : (
                <div className="flex h-16 w-24 shrink-0 items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50 text-xs text-slate-400">
                  Preview
                </div>
              )}
              <div className="min-w-[10rem] flex-1">
                <Input
                  placeholder="https://..."
                  value={url}
                  onChange={(e) => update(index, e.target.value)}
                  className="w-full min-w-0"
                  aria-label={`Image URL ${index + 1}`}
                />
              </div>
              <div className="flex flex-wrap items-center gap-1">
                {index === 0 ? (
                  <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">Cover</span>
                ) : (
                  <Button type="button" variant="secondary" className={smallButton} onClick={() => move(index, 0)}>
                    Make cover
                  </Button>
                )}
                <Button
                  type="button"
                  variant="secondary"
                  className={smallButton}
                  disabled={index === 0}
                  onClick={() => move(index, index - 1)}
                  aria-label={`Move image ${index + 1} up`}
                >
                  &uarr;
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  className={smallButton}
                  disabled={index === values.length - 1}
                  onClick={() => move(index, index + 1)}
                  aria-label={`Move image ${index + 1} down`}
                >
                  &darr;
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  className={smallButton}
                  onClick={() => onChange(values.filter((_, i) => i !== index))}
                >
                  Remove
                </Button>
              </div>
            </div>
            {invalid && <span className="text-sm text-red-600">The URL must start with http:// or https://</span>}
            {loadFailed && <span className="text-sm text-red-600">Couldn't load this image</span>}
            {duplicate && <span className="text-sm text-amber-600">This URL is already in the list</span>}
          </div>
        )
      })}
      {error && <span className="text-sm text-red-600">{error}</span>}
      <div>
        <Button
          type="button"
          variant="secondary"
          onClick={() => onChange([...values, ''])}
          disabled={values.length >= max}
        >
          Add Image URL
        </Button>
      </div>
    </div>
  )
}

/** Trims and drops blank rows before sending to the API. */
export function cleanImageUrls(urls: string[] | undefined): string[] {
  return (urls ?? []).map((url) => url.trim()).filter((url) => url.length > 0)
}
