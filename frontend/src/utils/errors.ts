import { isAxiosError } from 'axios'
import type { ErrorResponse } from '../types/auth'

/**
 * The server's message plus every field error as "field: message" — including fields
 * the form has no input for, which would otherwise fail silently.
 */
export function formatErrorResponse(data: ErrorResponse): string {
  const details = Object.entries(data.fieldErrors ?? {}).map(([field, message]) => `${field}: ${message}`)
  return details.length > 0 ? `${data.message} (${details.join('; ')})` : data.message
}

/** The server's error message (with field errors) when the failure came from the API, otherwise {@code fallback}. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError<ErrorResponse>(err) && err.response?.data?.message) {
    return formatErrorResponse(err.response.data)
  }
  return fallback
}
