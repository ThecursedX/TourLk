/**
 * Formats a backend LocalDate ("YYYY-MM-DD") for display. Parsed as a
 * local date on purpose: `new Date('2026-10-01')` is UTC midnight, which
 * shows as the previous day in timezones behind UTC.
 */
export function formatLocalDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/** Tomorrow as "YYYY-MM-DD" in local time — the earliest date the backend's @Future accepts. */
export function tomorrowIso(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
