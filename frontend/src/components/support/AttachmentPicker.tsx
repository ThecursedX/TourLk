import { useRef, useState, type ChangeEvent } from 'react'
import Button from '../ui/Button'
import {
  ALLOWED_TICKET_FILE_TYPES,
  MAX_TICKET_FILES,
  MAX_TICKET_FILE_BYTES,
} from '../../types/supportTicket'

interface AttachmentPickerProps {
  files: File[]
  onChange: (files: File[]) => void
  disabled?: boolean
  error?: string | null
}

/**
 * Attach up to 3 images/PDFs (5 MB each). Files are checked here so the user gets
 * instant feedback; the server re-validates everything (including file contents).
 */
export default function AttachmentPicker({ files, onChange, disabled, error }: AttachmentPickerProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [localError, setLocalError] = useState<string | null>(null)

  const handleSelect = (e: ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(e.target.files ?? [])
    e.target.value = '' // let the same file be picked again after removing it
    setLocalError(null)

    const next = [...files]
    for (const file of selected) {
      if (!ALLOWED_TICKET_FILE_TYPES.includes(file.type)) {
        setLocalError(`'${file.name}' is not allowed. Attach images (JPEG, PNG, GIF, WebP) or PDF files only.`)
        continue
      }
      if (file.size > MAX_TICKET_FILE_BYTES) {
        setLocalError(`'${file.name}' is larger than 5 MB.`)
        continue
      }
      if (next.length >= MAX_TICKET_FILES) {
        setLocalError(`You can attach at most ${MAX_TICKET_FILES} files.`)
        break
      }
      next.push(file)
    }
    onChange(next)
  }

  const shownError = error ?? localError

  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-medium text-slate-700">
        Attachments <span className="font-normal text-slate-400">(optional, up to {MAX_TICKET_FILES} images or PDFs, 5 MB each)</span>
      </span>
      {files.length > 0 && (
        <ul className="flex flex-col gap-1">
          {files.map((file, index) => (
            <li key={`${file.name}-${index}`} className="flex items-center justify-between gap-2 text-sm text-slate-700">
              <span className="truncate">
                {file.name} <span className="text-slate-400">({Math.max(1, Math.round(file.size / 1024))} KB)</span>
              </span>
              <button
                type="button"
                className="text-xs font-medium text-red-600 hover:underline"
                onClick={() => onChange(files.filter((_, i) => i !== index))}
                disabled={disabled}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ALLOWED_TICKET_FILE_TYPES.join(',')}
        onChange={handleSelect}
        className="hidden"
        disabled={disabled}
      />
      <div>
        <Button
          type="button"
          variant="secondary"
          disabled={disabled || files.length >= MAX_TICKET_FILES}
          onClick={() => inputRef.current?.click()}
        >
          Attach files
        </Button>
      </div>
      {shownError && <p className="text-sm text-red-600">{shownError}</p>}
    </div>
  )
}
