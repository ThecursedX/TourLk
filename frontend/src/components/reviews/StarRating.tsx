interface StarRatingProps {
  value: number
  onChange?: (value: number) => void
  size?: 'sm' | 'md'
}

const SIZE_CLASSES: Record<'sm' | 'md', string> = {
  sm: 'text-sm',
  md: 'text-2xl',
}

const STARS = [1, 2, 3, 4, 5]

export default function StarRating({ value, onChange, size = 'md' }: StarRatingProps) {
  const interactive = Boolean(onChange)

  return (
    <div
      className={`flex gap-0.5 leading-none ${SIZE_CLASSES[size]}`}
      role={interactive ? 'radiogroup' : undefined}
      aria-label={interactive ? 'Rating' : `Rated ${value} out of 5`}
    >
      {STARS.map((star) =>
        interactive ? (
          <button
            key={star}
            type="button"
            role="radio"
            aria-checked={star === value}
            aria-label={`${star} star${star === 1 ? '' : 's'}`}
            onClick={() => onChange?.(star)}
            className={star <= value ? 'text-amber-500' : 'text-slate-300 hover:text-amber-300'}
          >
            ★
          </button>
        ) : (
          <span key={star} className={star <= value ? 'text-amber-500' : 'text-slate-300'}>
            ★
          </span>
        ),
      )}
    </div>
  )
}
