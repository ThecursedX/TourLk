import type { ReactNode } from 'react'
import type { RoomResponseDto } from '../../types/accommodation'
import Card from '../ui/Card'
import ImageGallery from '../ui/ImageGallery'

interface RoomCardProps {
  room: RoomResponseDto
  footer?: ReactNode
}

export default function RoomCard({ room, footer }: RoomCardProps) {
  return (
    <Card className="flex min-w-0 flex-col gap-4">
      <div className="flex min-w-0 flex-col gap-4 md:flex-row">
        <div className="mx-auto w-full min-w-0 max-w-md md:w-72 md:shrink-0 md:self-center lg:w-80">
          <ImageGallery urls={room.imageUrls ?? []} alt={room.roomType} />
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <h4 className="break-words font-semibold text-slate-900">{room.roomType}</h4>
          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-sm text-slate-700">
            <span>Sleeps up to {room.maxOccupancy}</span>
            <span className="font-semibold text-slate-900">
              {room.pricePerNight.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
              <span className="font-normal text-slate-500"> / night</span>
            </span>
          </div>
          <p className="text-xs text-slate-500">{room.totalRooms} room(s) of this type</p>
          {(room.facilities?.length ?? 0) > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {room.facilities.map((facility) => (
                <span key={facility} className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                  {facility}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
      {footer && <div className="min-w-0 w-full">{footer}</div>}
    </Card>
  )
}
