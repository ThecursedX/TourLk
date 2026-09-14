import { useEffect, useState } from 'react'
import { browseDestinations } from '../../api/destinationApi'
import Select from '../ui/Select'
import type { DestinationResponseDto, DestinationSummary } from '../../types/destination'

interface DestinationSelectProps {
  id?: string
  label?: string
  /** Currently selected destination id, or '' / undefined when nothing is picked. */
  value: number | ''
  onChange: (destinationId: number | '') => void
  error?: string
  disabled?: boolean
  /** Blank first option label — omit to require a choice. */
  placeholder?: string
  /**
   * The destination currently saved on the entity being edited. If it has
   * since been deactivated it won't appear in the active list, so we still
   * surface it here (marked "(inactive)") rather than showing a blank select.
   */
  currentOption?: DestinationSummary | null
}

/**
 * Reusable dropdown of ACTIVE destinations. Replaces the old free-text
 * destination/location inputs on the Tour Package and Accommodation forms
 * and their list-page filters, so every reference points at a real
 * destination id.
 */
export default function DestinationSelect({
  id = 'destination',
  label,
  value,
  onChange,
  error,
  disabled,
  placeholder = 'Select a destination',
  currentOption,
}: DestinationSelectProps) {
  const [destinations, setDestinations] = useState<DestinationResponseDto[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    browseDestinations()
      .then(setDestinations)
      .catch(() => setLoadError('Could not load destinations.'))
  }, [])

  const activeIds = new Set(destinations.map((d) => d.id))
  const showStaleCurrent = currentOption != null && !activeIds.has(currentOption.id)

  return (
    <Select
      id={id}
      label={label}
      value={value === '' ? '' : String(value)}
      onChange={(e) => onChange(e.target.value === '' ? '' : Number(e.target.value))}
      error={error ?? loadError ?? undefined}
      disabled={disabled}
    >
      <option value="">{placeholder}</option>
      {showStaleCurrent && (
        <option value={currentOption!.id}>{currentOption!.name} (inactive)</option>
      )}
      {destinations.map((d) => (
        <option key={d.id} value={d.id}>
          {d.name} — {d.region}
        </option>
      ))}
    </Select>
  )
}
