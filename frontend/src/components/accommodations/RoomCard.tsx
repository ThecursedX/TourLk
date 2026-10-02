import type { ReactNode } from 'react'
import type { RoomResponseDto } from '../../types/accommodation'
import Card from '../ui/Card'

interface RoomCardProps {
  room: RoomResponseDto
  footer?: ReactNode
}

export default function RoomCard({ room, footer }: RoomCardProps) {
  return (
    <Card className="flex flex-col gap-2">
      {room.imageUrls?.[0] && (
        <img
          src={room.imageUrls[0]}
          alt=""
          className="h-32 w-full rounded-lg border border-slate-200 object-cover"
          onError={(e) => {
            e.currentTarget.style.display = 'none'
          }}
        />
      )}
      <h4 className="font-semibold text-slate-900">{room.roomType}</h4>
      <div className="flex items-center justify-between text-sm text-slate-700">
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
      {footer}
    </Card>
  )
}
