import type { ReactNode } from 'react'
import Navbar from './Navbar'

interface LayoutProps {
  children: ReactNode
}

export default function Layout({ children }: LayoutProps) {
  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <main className="mx-auto max-w-5xl px-4 py-8">{children}</main>
    </div>
  )
}
