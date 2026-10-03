import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createBooking } from '../../api/bookingApi'
import { getPackageDepartures } from '../../api/tourPackageApi'
import Button from '../ui/Button'
import Input from '../ui/Input'
import { formatLocalDate, tomorrowIso } from '../../utils/date'
import type { ErrorResponse } from '../../types/auth'
import { closedForTripMessage, closureWindowOf, tripOverlapsClosure } from '../../utils/closure'
import type { DestinationSummary } from '../../types/destination'
import type { PackageDepartureResponseDto } from '../../types/tourPackage'

interface BookNowFormProps {
  tourPackageId: number
  /** When true, the tourist must pick one of the package's departures instead of any date. */
  hasDepartures: boolean
  /** The package's destination and length, used to rule out dates inside a closure (the backend re-checks). */
  destination?: DestinationSummary
  durationDays?: number
}

export default function BookNowForm({ tourPackageId, hasDepartures, destination, durationDays = 1 }: BookNowFormProps) {
  const navigate = useNavigate()
  const [travelDate, setTravelDate] = useState('')
  const [numberOfTravelers, setNumberOfTravelers] = useState(1)
  const [specialRequests, setSpecialRequests] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [departures, setDepartures] = useState<PackageDepartureResponseDto[]>([])
  const [departuresLoading, setDeparturesLoading] = useState(hasDepartures)

  useEffect(() => {
    if (!hasDepartures) return
    setDeparturesLoading(true)
    getPackageDepartures(tourPackageId)
      .then(setDepartures)
      .catch(() => setFormError('Could not load departure dates. Please try again later.'))
      .finally(() => setDeparturesLoading(false))
  }, [tourPackageId, hasDepartures])

  const closure = closureWindowOf(destination)
  const isClosed = (date: string) => tripOverlapsClosure(date, durationDays, closure)
  const closedMessage = closure && destination && isClosed(travelDate) ? closedForTripMessage(destination.name, closure) : null

  const selectedDeparture = departures.find((d) => d.departureDate === travelDate)
  const noUpcomingDepartures = hasDepartures && !departuresLoading && departures.length === 0

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!travelDate) {
      errors.travelDate = hasDepartures ? 'Please choose a departure' : 'Travel date is required'
    } else if (closedMessage) {
      errors.travelDate = closedMessage
    }
    if (!numberOfTravelers || numberOfTravelers <= 0) {
      errors.numberOfTravelers = 'Number of travelers must be a positive number'
    } else if (selectedDeparture && numberOfTravelers > selectedDeparture.seatsLeft) {
      errors.numberOfTravelers = `Only ${selectedDeparture.seatsLeft} seat(s) left on this departure`
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      const created = await createBooking({
        tourPackageId,
        travelDate,
        numberOfTravelers,
        specialRequests: specialRequests.trim() || undefined,
      })
      navigate(`/checkout/booking/${created.id}`)
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err) && err.response) {
        setFormError(err.response.data.message)
        setFieldErrors(err.response.data.fieldErrors ?? {})
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const travelersInput = (
    <Input
      id="numberOfTravelers"
      label="Number of travelers"
      type="number"
      min={1}
      max={selectedDeparture?.seatsLeft}
      value={numberOfTravelers}
      onChange={(e) => setNumberOfTravelers(Number(e.target.value))}
      error={fieldErrors.numberOfTravelers}
    />
  )

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
      {hasDepartures ? (
        <>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium text-slate-700">Choose a departure</legend>
            {departuresLoading && <p className="text-sm text-slate-500">Loading departures...</p>}
            {noUpcomingDepartures && (
              <p className="text-sm text-slate-600">
                There are no upcoming departures for this package right now. Please check back later.
              </p>
            )}
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {departures.map((departure) => {
                const closed = isClosed(departure.departureDate)
                const full = departure.seatsLeft <= 0 || closed
                const selected = departure.departureDate === travelDate
                return (
                  <label
                    key={departure.id}
                    className={`flex items-center justify-between gap-3 rounded-xl border px-4 py-3 text-sm transition-colors ${
                      full
                        ? 'cursor-not-allowed border-slate-200 bg-slate-50 text-slate-400'
                        : selected
                          ? 'cursor-pointer border-blue-500 bg-blue-50 text-slate-900 ring-2 ring-blue-400'
                          : 'cursor-pointer border-slate-300 bg-white text-slate-900 hover:border-blue-400'
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <input
                        type="radio"
                        name="departure"
                        value={departure.departureDate}
                        checked={selected}
                        disabled={full}
                        onChange={() => setTravelDate(departure.departureDate)}
                        className="accent-blue-600"
                      />
                      <span className="font-medium">{formatLocalDate(departure.departureDate)}</span>
                    </span>
                    <span className={full ? 'font-semibold' : 'text-slate-500'}>
                      {closed ? 'Destination closed' : full ? 'Full' : `${departure.seatsLeft} seat${departure.seatsLeft === 1 ? '' : 's'} left`}
                    </span>
                  </label>
                )
              })}
            </div>
            {closure && destination && departures.some((d) => isClosed(d.departureDate)) && (
              <span className="text-sm text-orange-700">{closedForTripMessage(destination.name, closure)}</span>
            )}
            {fieldErrors.travelDate && <span className="text-sm text-red-600">{fieldErrors.travelDate}</span>}
          </fieldset>
          <div className="grid grid-cols-2 gap-4">{travelersInput}</div>
        </>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          <Input
            id="travelDate"
            label="Travel date"
            type="date"
            min={tomorrowIso()}
            value={travelDate}
            onChange={(e) => setTravelDate(e.target.value)}
            error={fieldErrors.travelDate ?? closedMessage ?? undefined}
          />
          {travelersInput}
        </div>
      )}
      <div className="flex flex-col gap-1">
        <label htmlFor="specialRequests" className="text-sm font-medium text-slate-700">
          Special requests (optional)
        </label>
        <textarea
          id="specialRequests"
          rows={3}
          value={specialRequests}
          onChange={(e) => setSpecialRequests(e.target.value)}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      <Button
        type="submit"
        disabled={submitting || departuresLoading || noUpcomingDepartures || closedMessage !== null}
        className="self-start"
      >
        {submitting ? 'Booking...' : 'Book Now'}
      </Button>
    </form>
  )
}
