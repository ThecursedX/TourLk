import type { ReactNode } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
  /** Shows a small notification dot in the corner — for cards that need the
   * viewer's attention (a pending approval, an unresolved ticket, a flagged
   * review) so it's visible without opening the item. */
  attention?: boolean
}

export default function Card({ children, className = '', attention = false }: CardProps) {
  return (
    <div className={`relative rounded-2xl border border-slate-200 bg-white p-6 shadow-soft ${className}`}>
      {attention && (
        <span className="absolute -right-1.5 -top-1.5 flex h-3.5 w-3.5" title="Needs attention">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-coral-400 opacity-75" />
          <span className="relative inline-flex h-3.5 w-3.5 rounded-full border-2 border-white bg-coral-500" />
        </span>
      )}
      {children}
    </div>
  )
}
