import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import Navbar from './Navbar'
import pageBackground from '../../assets/page-bg-surfers.jpg'

interface LayoutProps {
  children: ReactNode
}

/**
 * Every page except the homepage gets a shared backdrop photo (surfers in a
 * bay) fixed behind the content, lightly blurred and lightly washed so it
 * reads as a real photo rather than a vague smear, while the existing
 * white/glass cards stay just as readable as before. The homepage (at both
 * `/` and `/home`) keeps its own two hero/section images instead (see
 * HomePage.tsx) and sits flush behind the floating navbar with no gap.
 *
 * The navbar itself is `fixed` (not sticky-in-flow) so the homepage hero can
 * start at the very top of the page, behind it. Every other page compensates
 * with top padding on `<main>` so its content clears the floating pill.
 */
export default function Layout({ children }: LayoutProps) {
  const location = useLocation()
  const isHomepage = location.pathname === '/' || location.pathname === '/home'

  return (
    <div className={`relative min-h-screen ${isHomepage ? 'bg-slate-50' : ''}`}>
      {!isHomepage && (
        <div className="fixed inset-0 -z-10 overflow-hidden" aria-hidden="true">
          <img
            src={pageBackground}
            alt=""
            className="h-full w-full scale-110 object-cover blur-sm"
          />
          <div className="absolute inset-0 bg-slate-50/55" />
        </div>
      )}
      <Navbar />
      <main className={`relative mx-auto max-w-5xl px-4 ${isHomepage ? '' : 'pt-24 pb-8'}`}>{children}</main>
    </div>
  )
}
