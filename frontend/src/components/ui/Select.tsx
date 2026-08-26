import type { SelectHTMLAttributes } from 'react'

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
}

export default function Select({ label, error, id, className = '', children, ...props }: SelectProps) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={id} className="text-sm font-medium text-slate-700">
          {label}
        </label>
      )}
      <select
        id={id}
        className={`rounded-md border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
          error ? 'border-red-500' : 'border-slate-300'
        } ${className}`}
        {...props}
      >
        {children}
      </select>
      {error && <span className="text-sm text-red-600">{error}</span>}
    </div>
  )
}
