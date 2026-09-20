import type { ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-cobalt-600/90 text-white border border-white/25 shadow-soft hover:bg-cobalt-700/90 disabled:bg-cobalt-200 disabled:text-cobalt-500 disabled:shadow-none disabled:border-transparent',
  secondary:
    'bg-white/70 text-cobalt-700 border border-cobalt-200 hover:bg-white/90 hover:border-cobalt-300 disabled:text-slate-400 disabled:border-slate-200',
  ghost: 'bg-white/0 text-cobalt-700 hover:bg-white/40 disabled:text-slate-400',
}

export default function Button({ variant = 'primary', className = '', children, ...props }: ButtonProps) {
  return (
    <button
      className={`btn-glass relative overflow-hidden rounded-full px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed ${variantClasses[variant]} ${className}`}
      {...props}
    >
      <span className="relative z-10">{children}</span>
    </button>
  )
}
