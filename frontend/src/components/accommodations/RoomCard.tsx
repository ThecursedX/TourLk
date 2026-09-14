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
      <h4 className="font-semibold text-slate-900">{room.roomType}</h4>
      <div className="flex items-center justify-between text-sm text-slate-700">
        <span>Sleeps up to {room.maxOccupancy}</span>
        <span className="font-semibold text-slate-900">
          {room.pricePerNight.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
          <span className="font-normal text-slate-500"> / night</span>
        </span>
      </div>
      <p className="text-xs text-slate-500">{room.totalRooms} room(s) of this type</p>
      {footer}
    </Card>
  )
}
