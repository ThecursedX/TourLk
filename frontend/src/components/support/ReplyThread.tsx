import { useState } from 'react'
import { downloadAttachment } from '../../api/supportTicketApi'
import type { TicketReplyResponseDto } from '../../types/supportTicket'

interface ReplyThreadProps {
  ticketId: number
  replies: TicketReplyResponseDto[]
}

function formatSize(bytes: number): string {
  return bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

export default function ReplyThread({ ticketId, replies }: ReplyThreadProps) {
  const [downloadError, setDownloadError] = useState<string | null>(null)

  const handleDownload = async (attachmentId: number, fileName: string) => {
    setDownloadError(null)
    try {
      await downloadAttachment(ticketId, attachmentId, fileName)
    } catch {
      setDownloadError('That file could not be downloaded.')
    }
  }

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
            {(reply.attachments?.length ?? 0) > 0 && (
              <ul className="mt-3 flex flex-wrap gap-2">
                {reply.attachments.map((attachment) => (
                  <li key={attachment.id}>
                    <button
                      type="button"
                      onClick={() => handleDownload(attachment.id, attachment.fileName)}
                      className="rounded-lg border border-slate-300 bg-white px-2.5 py-1 text-xs text-blue-700 hover:bg-slate-50"
                    >
                      {attachment.fileName}{' '}
                      <span className="text-slate-500">({formatSize(attachment.sizeBytes)})</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )
      })}
      {downloadError && <p className="text-sm text-red-600">{downloadError}</p>}
    </div>
  )
}
