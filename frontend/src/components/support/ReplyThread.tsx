import type { TicketReplyResponseDto } from '../../types/supportTicket'

interface ReplyThreadProps {
  replies: TicketReplyResponseDto[]
}

export default function ReplyThread({ replies }: ReplyThreadProps) {
  return (
    <div className="flex flex-col gap-3">
      {replies.map((reply) => {
        const isAdmin = reply.authorRole === 'ADMIN'
        return (
          <div
            key={reply.id}
            className={`rounded-lg border p-4 ${isAdmin ? 'border-blue-200 bg-blue-50' : 'border-slate-200 bg-white'}`}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium text-slate-900">
                {reply.authorName}
                {isAdmin && <span className="ml-2 text-xs font-semibold text-blue-700">SUPPORT</span>}
              </span>
              <span className="text-xs text-slate-500">{new Date(reply.createdAt).toLocaleString()}</span>
            </div>
            <p className="mt-2 whitespace-pre-line text-sm text-slate-700">{reply.message}</p>
          </div>
        )
      })}
    </div>
  )
}
