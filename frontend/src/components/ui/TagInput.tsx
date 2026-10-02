import { useState, type KeyboardEvent } from 'react'

interface TagInputProps {
  label?: string
  values: string[]
  onChange: (values: string[]) => void
  placeholder?: string
  error?: string
}

/**
 * A chip-style list editor: type a value and press Enter/comma (or blur
 * the field) to add it as a tag, click a tag's × to remove it. Used for
 * short free-text lists (inclusions, exclusions, itinerary places) where
 * a full add/remove row per entry (see the image URL list) would be
 * overkill.
 */
export default function TagInput({ label, values, onChange, placeholder, error }: TagInputProps) {
  const [draft, setDraft] = useState('')

  const addTag = () => {
    const trimmed = draft.trim()
    if (trimmed && !values.includes(trimmed)) {
      onChange([...values, trimmed])
    }
    setDraft('')
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      addTag()
    } else if (e.key === 'Backspace' && draft === '' && values.length > 0) {
      onChange(values.slice(0, -1))
    }
  }

  const removeTag = (index: number) => {
    onChange(values.filter((_, i) => i !== index))
  }

  return (
    <div className="flex flex-col gap-1">
      {label && <label className="text-sm font-medium text-slate-700">{label}</label>}
      <div
        className={`flex flex-wrap items-center gap-2 rounded-xl border bg-white px-3 py-2 focus-within:border-blue-400 focus-within:ring-2 focus-within:ring-blue-400 ${
          error ? 'border-red-500' : 'border-slate-300'
        }`}
      >
        {values.map((value, index) => (
          <span
            key={`${value}-${index}`}
            className="flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700"
          >
            {value}
            <button
              type="button"
              onClick={() => removeTag(index)}
              className="text-blue-400 hover:text-blue-700"
              aria-label={`Remove ${value}`}
            >
              &times;
            </button>
          </span>
        ))}
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={addTag}
          placeholder={values.length === 0 ? placeholder : ''}
          className="min-w-[140px] flex-1 border-none bg-transparent py-0.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
        />
      </div>
      {error && <span className="text-sm text-red-600">{error}</span>}
    </div>
  )
}
