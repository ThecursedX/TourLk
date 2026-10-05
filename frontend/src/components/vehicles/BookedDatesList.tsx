import type { BookedDateRange } from '../../types/vehicle'
import { formatShortDate } from '../../utils/date'

interface BookedDatesListProps {
  ranges: BookedDateRange[]
}

export default function BookedDatesList({ ranges }: BookedDatesListProps) {
  if (ranges.length === 0) return null

  return (
    <div className="flex flex-col gap-1">
      <h3 className="text-sm font-medium text-slate-700">Booked dates</h3>
      <ul className="flex flex-col gap-0.5 text-sm text-slate-600">
        {ranges.map((range) => (
          <li key={`${range.startDate}-${range.endDate}`}>
            Not available: {formatShortDate(range.startDate)} &ndash; {formatShortDate(range.endDate)}
          </li>
        ))}
      </ul>
    </div>
  )
}
