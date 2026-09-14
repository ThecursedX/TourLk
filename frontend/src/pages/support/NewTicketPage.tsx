import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createTicket } from '../../api/supportTicketApi'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import type { ErrorResponse } from '../../types/auth'
import {
  TICKET_CATEGORIES,
  TICKET_PRIORITIES,
  type TicketCategory,
  type TicketPriority,
} from '../../types/supportTicket'

export default function NewTicketPage() {
  const navigate = useNavigate()
  const [subject, setSubject] = useState('')
  const [category, setCategory] = useState<TicketCategory>('OTHER')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')
  const [message, setMessage] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const validate = (): boolean => {
    const errors: Record<string, string> = {}
    if (!subject.trim()) errors.subject = 'Subject is required'
    if (!message.trim()) errors.message = 'Message is required'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      const created = await createTicket({ subject, category, priority, message })
      navigate(`/support/${created.id}`)
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

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-2xl">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">New Support Ticket</h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <Input
            id="subject"
            label="Subject"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            error={fieldErrors.subject}
          />
          <div className="grid grid-cols-2 gap-4">
            <Select
              id="category"
              label="Category"
              value={category}
              onChange={(e) => setCategory(e.target.value as TicketCategory)}
            >
              {TICKET_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
            <Select
              id="priority"
              label="Priority"
              value={priority}
              onChange={(e) => setPriority(e.target.value as TicketPriority)}
            >
              {TICKET_PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </Select>
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="message" className="text-sm font-medium text-slate-700">
              Message
            </label>
            <textarea
              id="message"
              rows={5}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              className={`rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                fieldErrors.message ? 'border-red-500' : 'border-slate-300'
              }`}
            />
            {fieldErrors.message && <span className="text-sm text-red-600">{fieldErrors.message}</span>}
          </div>
          {formError && <p className="text-sm text-red-600">{formError}</p>}
          <Button type="submit" disabled={submitting} className="self-start">
            {submitting ? 'Submitting...' : 'Submit Ticket'}
          </Button>
        </form>
      </Card>
    </div>
  )
}
