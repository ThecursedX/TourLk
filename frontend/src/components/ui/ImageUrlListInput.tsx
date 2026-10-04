import Button from './Button'
import Input from './Input'

interface ImageUrlListInputProps {
  label?: string
  values: string[]
  onChange: (values: string[]) => void
  max?: number
  error?: string
}

/**
 * Add/remove list of image URLs with a small preview per row. Same editor
 * the package form uses, shared so vehicles, properties and rooms all cap
 * at the same limit. Blank rows are kept while editing; callers should
 * drop them on submit (see {@link cleanImageUrls}).
 */
export default function ImageUrlListInput({
  label = 'Images (optional)',
  values,
  onChange,
  max = 10,
  error,
}: ImageUrlListInputProps) {
  const update = (index: number, url: string) => {
    const next = [...values]
    next[index] = url
    onChange(next)
  }

  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-medium text-slate-700">
        {label} <span className="font-normal text-slate-400">(up to {max})</span>
      </span>
      {values.map((url, index) => (
        <div key={index} className="flex items-center gap-2">
          {url.trim() && (
            <img
              src={url}
              alt=""
              className="h-12 w-12 shrink-0 rounded-lg border border-slate-200 object-cover"
              onError={(e) => {
                e.currentTarget.style.visibility = 'hidden'
              }}
            />
          )}
          <Input
            placeholder="https://..."
            value={url}
            onChange={(e) => update(index, e.target.value)}
            className="flex-1"
          />
          <Button
            type="button"
            variant="secondary"
            onClick={() => onChange(values.filter((_, i) => i !== index))}
          >
            Remove
          </Button>
        </div>
      ))}
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
