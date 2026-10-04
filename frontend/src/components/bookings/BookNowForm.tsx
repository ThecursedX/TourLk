import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createBooking } from '../../api/bookingApi'
import { getAddOnAvailability, getPackageDepartures } from '../../api/tourPackageApi'
import Button from '../ui/Button'
import Input from '../ui/Input'
import { addDaysIso, formatLocalDate, formatShortDate, tomorrowIso } from '../../utils/date'
import type { ErrorResponse } from '../../types/auth'
import { closedForTripMessage, closureWindowOf, tripOverlapsClosure } from '../../utils/closure'
import type { DestinationSummary } from '../../types/destination'
import type {
  AddOnAvailabilityDto,
  PackageAddOnResponseDto,
  PackageDepartureResponseDto,
} from '../../types/tourPackage'

interface BookNowFormProps {
  tourPackageId: number
  /** When true, the tourist must pick one of the package's departures instead of any date. */
  hasDepartures: boolean
  /** The package's destination and length, used to rule out dates inside a closure (the backend re-checks). */
  destination?: DestinationSummary
  durationDays?: number
  /** Package price per traveler, for the live price breakdown. */
  price?: number
  /** Optional hotel rooms / vehicles the guide attached to the package. */
  addOns?: PackageAddOnResponseDto[]
}

const money = (n: number) => n.toLocaleString(undefined, { style: 'currency', currency: 'USD' })

export default function BookNowForm({
  tourPackageId,
  hasDepartures,
  destination,
  durationDays = 1,
  price = 0,
  addOns = [],
}: BookNowFormProps) {
  const navigate = useNavigate()
  const [travelDate, setTravelDate] = useState('')
  const [numberOfTravelers, setNumberOfTravelers] = useState(1)
  const [specialRequests, setSpecialRequests] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [departures, setDepartures] = useState<PackageDepartureResponseDto[]>([])
  const [departuresLoading, setDeparturesLoading] = useState(hasDepartures)
  // Chosen add-ons: room id -> number of rooms; vehicle ids.
  const [pickedRooms, setPickedRooms] = useState<Record<number, number>>({})
  const [pickedVehicles, setPickedVehicles] = useState<number[]>([])
  const [availability, setAvailability] = useState<AddOnAvailabilityDto[]>([])

  useEffect(() => {
    if (!hasDepartures) return
    setDeparturesLoading(true)
    getPackageDepartures(tourPackageId)
      .then(setDepartures)
      .catch(() => setFormError('Could not load departure dates. Please try again later.'))
      .finally(() => setDeparturesLoading(false))
  }, [tourPackageId, hasDepartures])

  // The trip's dates decide the add-on dates: rooms for (durationDays - 1) nights (at least one),
  // vehicles for every day of the trip. Mirrors AddOnDates on the backend.
  const nights = Math.max(1, durationDays - 1)
  const checkOut = travelDate ? addDaysIso(travelDate, nights) : ''
  const vehicleEnd = travelDate ? addDaysIso(travelDate, Math.max(1, durationDays) - 1) : ''

  useEffect(() => {
    if (!travelDate || addOns.length === 0) {
      setAvailability([])
      return
    }
    let cancelled = false
    getAddOnAvailability(tourPackageId, travelDate)
      .then((result) => {
        if (cancelled) return
        setAvailability(result)
        // Drop picks that are not free on the newly chosen dates.
        setPickedRooms((prev) =>
          Object.fromEntries(
            Object.entries(prev).filter(([roomId]) =>
              result.some((a) => a.roomId === Number(roomId) && a.available),
            ),
          ),
        )
        setPickedVehicles((prev) => prev.filter((id) => result.some((a) => a.vehicleId === id && a.available)))
      })
      // The backend re-checks on submit, so a failed lookup just leaves everything selectable.
      .catch(() => !cancelled && setAvailability([]))
    return () => {
      cancelled = true
    }
  }, [tourPackageId, travelDate, addOns.length])

  const roomInfo = (roomId: number) => availability.find((a) => a.roomId === roomId)
  const vehicleInfo = (vehicleId: number) => availability.find((a) => a.vehicleId === vehicleId)

  const roomsSubtotal = addOns.reduce(
    (sum, a) => (a.room && pickedRooms[a.room.id] ? sum + a.room.pricePerNight * nights * pickedRooms[a.room.id] : sum),
    0,
  )
  const vehiclesSubtotal = addOns.reduce(
    (sum, a) => (a.vehicle && pickedVehicles.includes(a.vehicle.id) ? sum + a.vehicle.pricePerDay * durationDays : sum),
    0,
  )
  const packageSubtotal = price * (numberOfTravelers > 0 ? numberOfTravelers : 0)
  const total = packageSubtotal + roomsSubtotal + vehiclesSubtotal

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
        addOnRooms: Object.entries(pickedRooms).map(([roomId, count]) => ({
          roomId: Number(roomId),
          numberOfRooms: count,
        })),
        addOnVehicleIds: pickedVehicles,
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
      {addOns.length > 0 && (
        <fieldset className="flex flex-col gap-2 border-t border-slate-200 pt-4">
          <legend className="mb-1 text-sm font-medium text-slate-700">Optional extras</legend>
          {!travelDate ? (
            <p className="text-sm text-slate-500">Choose a date to see which extras are available.</p>
          ) : (
            <p className="text-sm text-slate-600">
              Check-in {formatShortDate(travelDate)}, check-out {formatShortDate(checkOut)} for rooms; vehicles are
              held {formatShortDate(travelDate)} &ndash; {formatShortDate(vehicleEnd)}.
            </p>
          )}
          {addOns.map((addOn) => {
            if (addOn.room) {
              const room = addOn.room
              const info = roomInfo(room.id)
              const unavailable = !travelDate || (info !== undefined && (!info.available || (info.roomsLeft ?? 1) <= 0))
              const count = pickedRooms[room.id]
              const maxRooms = info?.roomsLeft ?? room.totalRooms
              return (
                <div key={addOn.id} className={`flex flex-col gap-1 rounded-xl border px-4 py-3 text-sm ${unavailable ? 'border-slate-200 bg-slate-50 text-slate-400' : 'border-slate-300 text-slate-800'}`}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        disabled={unavailable}
                        checked={count !== undefined}
                        onChange={(e) =>
                          setPickedRooms((prev) => {
                            const next = { ...prev }
                            if (e.target.checked) next[room.id] = 1
                            else delete next[room.id]
                            return next
                          })
                        }
                        className="accent-blue-600"
                      />
                      <span>
                        {room.accommodationName} &middot; {room.roomType} &middot; {money(room.pricePerNight)}/night
                      </span>
                    </label>
                    {travelDate && unavailable && <span className="font-semibold">Unavailable on these dates</span>}
                    {count !== undefined && (
                      <label className="flex items-center gap-2">
                        Rooms
                        <input
                          type="number"
                          min={1}
                          max={maxRooms}
                          value={count}
                          onChange={(e) =>
                            setPickedRooms((prev) => ({
                              ...prev,
                              [room.id]: Math.min(Math.max(1, Number(e.target.value) || 1), maxRooms),
                            }))
                          }
                          className="w-16 rounded-md border border-slate-300 px-2 py-1 text-sm"
                        />
                      </label>
                    )}
                  </div>
                  {addOn.note && <span className="text-xs text-slate-500">{addOn.note}</span>}
                </div>
              )
            }
            if (addOn.vehicle) {
              const vehicle = addOn.vehicle
              const info = vehicleInfo(vehicle.id)
              const unavailable = !travelDate || (info !== undefined && !info.available)
              return (
                <div key={addOn.id} className={`flex flex-col gap-1 rounded-xl border px-4 py-3 text-sm ${unavailable ? 'border-slate-200 bg-slate-50 text-slate-400' : 'border-slate-300 text-slate-800'}`}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        disabled={unavailable}
                        checked={pickedVehicles.includes(vehicle.id)}
                        onChange={(e) =>
                          setPickedVehicles((prev) =>
                            e.target.checked ? [...prev, vehicle.id] : prev.filter((id) => id !== vehicle.id),
                          )
                        }
                        className="accent-blue-600"
                      />
                      <span>
                        {vehicle.make} {vehicle.model} &middot; {vehicle.seatingCapacity} seats &middot;{' '}
                        {money(vehicle.pricePerDay)}/day
                      </span>
                    </label>
                    {travelDate && unavailable && <span className="font-semibold">Unavailable on these dates</span>}
                  </div>
                  {addOn.note && <span className="text-xs text-slate-500">{addOn.note}</span>}
                </div>
              )
            }
            return null
          })}
        </fieldset>
      )}
      {price > 0 && (
        <dl className="flex flex-col gap-0.5 rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-700">
          <div className="flex justify-between">
            <dt>
              Package ({numberOfTravelers} &times; {money(price)})
            </dt>
            <dd>{money(packageSubtotal)}</dd>
          </div>
          {roomsSubtotal > 0 && (
            <div className="flex justify-between">
              <dt>Hotel rooms ({nights} night{nights === 1 ? '' : 's'})</dt>
              <dd>{money(roomsSubtotal)}</dd>
            </div>
          )}
          {vehiclesSubtotal > 0 && (
            <div className="flex justify-between">
              <dt>Vehicles ({durationDays} day{durationDays === 1 ? '' : 's'})</dt>
              <dd>{money(vehiclesSubtotal)}</dd>
            </div>
          )}
          <div className="flex justify-between border-t border-slate-200 pt-1 font-semibold text-slate-900">
            <dt>Total</dt>
            <dd>{money(total)}</dd>
          </div>
        </dl>
      )}
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
