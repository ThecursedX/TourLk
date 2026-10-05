import { formatLocalDate } from './date'

/** The closure fields a destination (or the destination nested in a package) carries. */
export interface ClosureInfo {
  status: string
  closureFrom?: string | null
  closureUntil?: string | null
}

export function todayIso(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function addDaysIso(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const d = new Date(year, month - 1, day + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export interface ClosureWindow {
  from: string
  until: string | null
}

/** The closure window of a TEMPORARILY_CLOSED destination (a missing start means today); null otherwise. */
export function closureWindowOf(destination: ClosureInfo | null | undefined): ClosureWindow | null {
  if (!destination || destination.status !== 'TEMPORARILY_CLOSED') return null
  return { from: destination.closureFrom ?? todayIso(), until: destination.closureUntil ?? null }
}

/** True when a trip starting on travelDate and lasting durationDays shares a day with the closure. ISO dates compare as strings. */
export function tripOverlapsClosure(travelDate: string, durationDays: number, window: ClosureWindow | null): boolean {
  if (!window || !travelDate) return false
  const tripEnd = addDaysIso(travelDate, Math.max(durationDays, 1) - 1)
  return (window.until === null || travelDate <= window.until) && tripEnd >= window.from
}

/** Same wording as the backend booking check. */
export function closedForTripMessage(destinationName: string, window: ClosureWindow): string {
  const range = window.until
    ? `from ${formatLocalDate(window.from)} to ${formatLocalDate(window.until)}`
    : `from ${formatLocalDate(window.from)} until further notice`
  return `${destinationName} is closed ${range}, so this package can't be booked for those dates.`
}

/** "Closed from X until Y", "Closed until Y", "Closed from X" or "Temporarily closed". */
export function closureLabel(destination: ClosureInfo): string {
  const from = destination.closureFrom ? formatLocalDate(destination.closureFrom) : null
  const until = destination.closureUntil ? formatLocalDate(destination.closureUntil) : null
  if (from && until) return `Closed from ${from} until ${until}`
  if (until) return `Closed until ${until}`
  if (from) return `Closed from ${from}`
  return 'Temporarily closed'
}
