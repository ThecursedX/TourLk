import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="text-center">
      <h1 className="text-2xl font-semibold text-slate-900">404 — Page not found</h1>
      <Link to="/" className="mt-4 inline-block text-blue-600 hover:underline">
        Back home
      </Link>
    </div>
  )
}
