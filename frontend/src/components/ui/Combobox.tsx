import { useId, type InputHTMLAttributes } from 'react'

interface ComboboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'list'> {
  label?: string
  error?: string
  /** Suggested values shown in the dropdown; free text is still allowed. */
  options: string[]
}

/**
 * A text input with datalist-backed suggestions: free typing is always
 * allowed, but the browser offers `options` as autocomplete suggestions.
 * Used where a fixed <Select> would be too rigid (e.g. Category, an
 * open-ended vocabulary that grows as admins add destinations) but a
 * curated suggestion list is still useful.
 */
export default function Combobox({ label, error, id, className = '', options, ...props }: ComboboxProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const listId = `${inputId}-options`

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-slate-700">
          {label}
        </label>
      )}
      <input
        id={inputId}
        list={listId}
        className={`rounded-xl border bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-blue-400 ${
          error ? 'border-red-500' : 'border-slate-300'
        } ${className}`}
        {...props}
      />
      <datalist id={listId}>
        {options.map((option) => (
          <option key={option} value={option} />
        ))}
      </datalist>
      {error && <span className="text-sm text-red-600">{error}</span>}
    </div>
  )
}
