import { useEffect, useState } from 'react'
import { browseAccommodations } from '../../api/accommodationApi'
import { browseVehicles } from '../../api/vehicleApi'
import type { AccommodationResponseDto } from '../../types/accommodation'
import type { PackageAddOnItemDto } from '../../types/tourPackage'
import type { VehicleResponseDto } from '../../types/vehicle'

interface AddOnPickerProps {
  /** Rooms are limited to hotels at this destination, so nothing is shown until one is chosen. */
  destinationId: number | ''
  value: PackageAddOnItemDto[]
  onChange: (next: PackageAddOnItemDto[]) => void
}

const money = (n: number) => n.toLocaleString(undefined, { style: 'currency', currency: 'USD' })

export default function AddOnPicker({ destinationId, value, onChange }: AddOnPickerProps) {
  const [hotels, setHotels] = useState<AccommodationResponseDto[]>([])
  const [vehicles, setVehicles] = useState<VehicleResponseDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (destinationId === '') {
      setHotels([])
      return
    }
    setLoading(true)
    setError(null)
    Promise.all([browseAccommodations({ locationId: destinationId }), browseVehicles()])
      .then(([accommodations, allVehicles]) => {
        setHotels(accommodations.filter((a) => a.status === 'ACTIVE' && a.rooms.length > 0))
        setVehicles(allVehicles.filter((v) => v.status === 'AVAILABLE'))
      })
      .catch(() => setError('Could not load hotels and vehicles. Please try again later.'))
      .finally(() => setLoading(false))
  }, [destinationId])

  const noteOf = (match: (item: PackageAddOnItemDto) => boolean) => value.find(match)?.note ?? ''
  const isRoomSelected = (roomId: number) => value.some((i) => i.roomId === roomId)
  const isVehicleSelected = (vehicleId: number) => value.some((i) => i.vehicleId === vehicleId)

  const toggleRoom = (roomId: number) =>
    onChange(isRoomSelected(roomId) ? value.filter((i) => i.roomId !== roomId) : [...value, { roomId }])
  const toggleVehicle = (vehicleId: number) =>
    onChange(
      isVehicleSelected(vehicleId) ? value.filter((i) => i.vehicleId !== vehicleId) : [...value, { vehicleId }],
    )
  const setNote = (match: (item: PackageAddOnItemDto) => boolean, note: string) =>
    onChange(value.map((i) => (match(i) ? { ...i, note } : i)))

  return (
    <div className="flex flex-col gap-3 border-t border-slate-200 pt-4">
      <div>
        <span className="text-sm font-medium text-slate-700">Add-ons (optional)</span>
        <p className="text-xs text-slate-500">
          Hotel rooms and vehicles tourists can add to this package when booking. Rooms are limited to hotels at the
          package destination.
        </p>
      </div>

      {destinationId === '' && <p className="text-sm text-slate-500">Choose a destination to pick hotels.</p>}
      {loading && <p className="text-sm text-slate-500">Loading...</p>}
      {error && <p className="text-sm text-red-600">{error}</p>}

      {destinationId !== '' && !loading && !error && (
        <>
          <div className="flex flex-col gap-2">
            <h4 className="text-sm font-semibold text-slate-900">Hotel rooms</h4>
            {hotels.length === 0 && <p className="text-sm text-slate-500">No approved hotels at this destination yet.</p>}
            {hotels.map((hotel) => (
              <div key={hotel.id} className="rounded-xl border border-slate-200 p-3">
                <p className="text-sm font-medium text-slate-900">{hotel.name}</p>
                <div className="mt-2 flex flex-col gap-2">
                  {hotel.rooms.map((room) => {
                    const selected = isRoomSelected(room.id)
                    return (
                      <div key={room.id} className="flex flex-col gap-1">
                        <label className="flex items-center gap-2 text-sm text-slate-700">
                          <input
                            type="checkbox"
                            checked={selected}
                            onChange={() => toggleRoom(room.id)}
                            className="accent-blue-600"
                          />
                          <span>
                            {room.roomType} &middot; {money(room.pricePerNight)}/night &middot; sleeps{' '}
                            {room.maxOccupancy}
                          </span>
                        </label>
                        {selected && (
                          <input
                            placeholder="Note for tourists (optional)"
                            maxLength={300}
                            value={noteOf((i) => i.roomId === room.id)}
                            onChange={(e) => setNote((i) => i.roomId === room.id, e.target.value)}
                            className="ml-6 rounded-md border border-slate-300 px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>

          <div className="flex flex-col gap-2">
            <h4 className="text-sm font-semibold text-slate-900">Vehicles</h4>
            {vehicles.length === 0 && <p className="text-sm text-slate-500">No approved vehicles available.</p>}
            {vehicles.map((vehicle) => {
              const selected = isVehicleSelected(vehicle.id)
              return (
                <div key={vehicle.id} className="flex flex-col gap-1 rounded-xl border border-slate-200 p-3">
                  <label className="flex items-center gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      checked={selected}
                      onChange={() => toggleVehicle(vehicle.id)}
                      className="accent-blue-600"
                    />
                    <span>
                      {vehicle.make} {vehicle.model} &middot; {vehicle.vehicleType} &middot; {vehicle.seatingCapacity}{' '}
                      seats &middot; {money(vehicle.pricePerDay)}/day
                    </span>
                  </label>
                  {selected && (
                    <input
                      placeholder="Note for tourists (optional)"
                      maxLength={300}
                      value={noteOf((i) => i.vehicleId === vehicle.id)}
                      onChange={(e) => setNote((i) => i.vehicleId === vehicle.id, e.target.value)}
                      className="ml-6 rounded-md border border-slate-300 px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  )}
                </div>
              )
            })}
          </div>
        </>
      )}
    </div>
  )
}
