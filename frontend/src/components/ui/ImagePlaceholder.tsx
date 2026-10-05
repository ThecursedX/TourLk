interface ImagePlaceholderProps {
  className?: string
}

/** Neutral stand-in used when there is no photo (or none of them load), same box as the real image. */
export default function ImagePlaceholder({ className = '' }: ImagePlaceholderProps) {
  return (
    <div
      className={`flex aspect-[16/10] w-full flex-col items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-slate-100 text-slate-400 ${className}`}
      role="img"
      aria-label="No photo yet"
    >
      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
        <rect x="3" y="4" width="18" height="16" rx="3" />
        <circle cx="9" cy="10" r="1.8" />
        <path d="M4 18l5-5 4 4 3-3 4 4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="text-xs font-medium">No photo yet</span>
    </div>
  )
}
