import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { browsePackages } from '../api/tourPackageApi'
import PackageCard from '../components/packages/PackageCard'
import type { TourPackageResponseDto } from '../types/tourPackage'
import heroImage from '../assets/hero-sailboat.jpg'
import section2Image from '../assets/hero-banana-leaves.jpg'

/**
 * As the page scrolls past the hero, `progress` runs 0 -> 1 over roughly
 * the first half of the hero's height. The "SRI LANKA" headline uses it to
 * fade, sink and blur into the wave imagery beneath it, instead of simply
 * scrolling off screen.
 */
function useHeroDissolve() {
  const heroRef = useRef<HTMLDivElement>(null)
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    let ticking = false

    const measure = () => {
      ticking = false
      const el = heroRef.current
      if (!el) return
      const dissolveDistance = Math.max(el.offsetHeight * 0.55, 1)
      const next = Math.min(1, Math.max(0, window.scrollY / dissolveDistance))
      setProgress(next)
    }

    const onScroll = () => {
      if (!ticking) {
        ticking = true
        requestAnimationFrame(measure)
      }
    }

    measure()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [])

  return { heroRef, progress }
}

export default function HomePage() {
  const [featured, setFeatured] = useState<TourPackageResponseDto[]>([])
  const { heroRef, progress } = useHeroDissolve()

  useEffect(() => {
    let cancelled = false
    browsePackages()
      .then((data) => {
        if (!cancelled) setFeatured(Array.isArray(data) ? data.slice(0, 3) : [])
      })
      .catch(() => {
        /* homepage stays useful even if the featured strip can't load */
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="flex flex-col">
      {/* Full-bleed hero — sits flush behind the floating navbar with no gap,
          and butts directly against Section 2 below with no seam. */}
      <div className="relative left-1/2 right-1/2 -mx-[50vw] w-screen">
        <div ref={heroRef} className="relative h-[85vh] min-h-[560px] overflow-hidden sm:h-screen">
          <img
            src={heroImage}
            alt="A sailboat crossing the deep blue waters off the Sri Lankan coast"
            className="absolute inset-0 h-full w-full object-cover"
          />
          <div
            className="absolute inset-0"
            style={{
              background:
                'linear-gradient(180deg, rgba(6,20,23,0.35) 0%, rgba(6,20,23,0.18) 35%, rgba(6,20,23,0.55) 100%)',
            }}
          />

          <div className="relative z-10 flex h-full flex-col items-center justify-center gap-5 px-4 text-center">
            <div
              style={{
                opacity: 1 - progress,
                transform: `translateY(${progress * 70}px) scale(${1 + progress * 0.08})`,
                filter: `blur(${progress * 9}px)`,
                letterSpacing: `${progress * 0.12}em`,
              }}
              className="flex flex-col items-center gap-4"
            >
              <h1 className="font-display text-6xl font-extrabold tracking-wide text-white drop-shadow-[0_6px_30px_rgba(0,0,0,0.35)] sm:text-7xl md:text-8xl">
                SRI LANKA
              </h1>
              <p className="max-w-md text-lg font-medium text-white/90 drop-shadow-[0_2px_10px_rgba(0,0,0,0.35)] sm:text-xl">
                Discover the island, your way.
              </p>
            </div>

            <div className="flex flex-wrap justify-center gap-3 pt-4">
              <Link
                to="/packages"
                className="btn-glass relative overflow-hidden rounded-full border border-white/30 bg-cobalt-600/80 px-7 py-3 text-sm font-semibold text-white shadow-soft transition-colors hover:bg-cobalt-700/85"
              >
                <span className="relative z-10">Plan my trip</span>
              </Link>
              <Link
                to="/register"
                className="btn-glass relative overflow-hidden rounded-full border border-white/40 bg-white/15 px-7 py-3 text-sm font-semibold text-white transition-colors hover:bg-white/25"
              >
                <span className="relative z-10">Create an account</span>
              </Link>
            </div>
          </div>
        </div>

        {/* trust strip */}
        <div className="flex flex-wrap items-center justify-center gap-x-10 gap-y-2 bg-blue-700 px-6 py-5 text-center">
          <span className="text-xs font-medium text-blue-100 sm:text-sm">Verified hotel &amp; homestay partners</span>
          <span className="text-xs font-medium text-blue-100 sm:text-sm">Licensed local tour guides</span>
          <span className="text-xs font-medium text-blue-100 sm:text-sm">Vetted vehicles &amp; drivers</span>
          <span className="text-xs font-medium text-blue-100 sm:text-sm">Secure online payments</span>
        </div>
      </div>

      {/* Section 2 — banana-leaf backed backdrop for the rest of the homepage */}
      <div className="relative left-1/2 right-1/2 -mx-[50vw] w-screen">
        <img
          src={section2Image}
          alt=""
          aria-hidden="true"
          className="absolute inset-0 h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-slate-900/55 via-slate-900/60 to-slate-900/55" />

        <div className="relative z-10 mx-auto flex w-full max-w-5xl flex-col gap-16 px-4 py-16">
          {/* Categories */}
          <div className="flex flex-col gap-6">
            <div>
              <h2 className="font-display text-2xl font-bold text-white">Everything for your trip, in one place</h2>
              <p className="mt-1 text-white/90">Browse by category, or let a local guide plan it for you.</p>
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Link
                to="/destinations"
                className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-soft transition-shadow hover:shadow-md"
              >
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-cobalt-50">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                    <path
                      d="M9 20L3 17.5V6.5L9 4L15 6.5L21 4V15"
                      stroke="#1B3FC2"
                      strokeWidth="1.8"
                      strokeLinejoin="round"
                    />
                    <path d="M9 4V20" stroke="#1B3FC2" strokeWidth="1.8" />
                    <path d="M15 6.5V13" stroke="#1B3FC2" strokeWidth="1.8" />
                    <circle cx="18.5" cy="17.5" r="3" stroke="#1B3FC2" strokeWidth="1.8" />
                    <path d="M20.6 19.6L22.5 21.5" stroke="#1B3FC2" strokeWidth="1.8" strokeLinecap="round" />
                  </svg>
                </div>
                <h3 className="font-display text-lg font-bold text-slate-900">Destinations</h3>
                <p className="text-sm text-slate-600">Explore the places you can visit across the island.</p>
              </Link>
              <Link
                to="/packages"
                className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-soft transition-shadow hover:shadow-md"
              >
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-50">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                    <path
                      d="M12 21C12 21 5 14.5 5 9.5C5 5.9 8 3 12 3C16 3 19 5.9 19 9.5C19 14.5 12 21 12 21Z"
                      stroke="#0F6259"
                      strokeWidth="1.8"
                    />
                    <circle cx="12" cy="9.5" r="2.6" stroke="#0F6259" strokeWidth="1.8" />
                  </svg>
                </div>
                <h3 className="font-display text-lg font-bold text-slate-900">Tour Packages</h3>
                <p className="text-sm text-slate-600">Multi-day itineraries led by licensed local guides.</p>
              </Link>
              <Link
                to="/accommodations"
                className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-soft transition-shadow hover:shadow-md"
              >
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-50">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                    <path d="M3 19V9.5C3 8.7 3.7 8 4.5 8H12V19" stroke="#A16A1F" strokeWidth="1.8" />
                    <path d="M12 12H19.5C20.3 12 21 12.7 21 13.5V19" stroke="#A16A1F" strokeWidth="1.8" />
                    <path d="M3 19H21" stroke="#A16A1F" strokeWidth="1.8" />
                  </svg>
                </div>
                <h3 className="font-display text-lg font-bold text-slate-900">Stays</h3>
                <p className="text-sm text-slate-600">Hotels, villas and homestays from vetted partners.</p>
              </Link>
              <Link
                to="/vehicles"
                className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-soft transition-shadow hover:shadow-md"
              >
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-sky-50">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                    <path
                      d="M4 16L5.5 10.5C5.8 9.6 6.6 9 7.5 9H16.5C17.4 9 18.2 9.6 18.5 10.5L20 16"
                      stroke="#25626C"
                      strokeWidth="1.8"
                      strokeLinecap="round"
                    />
                    <rect x="3" y="16" width="18" height="4" rx="1.4" stroke="#25626C" strokeWidth="1.8" />
                  </svg>
                </div>
                <h3 className="font-display text-lg font-bold text-slate-900">Vehicles &amp; Drivers</h3>
                <p className="text-sm text-slate-600">Hire a car, van or tuk-tuk with an experienced driver.</p>
              </Link>
            </div>
          </div>

          {/* Featured packages (real data, best-effort) */}
          {featured.length > 0 && (
            <div className="flex flex-col gap-6">
              <div className="flex items-end justify-between">
                <div>
                  <h2 className="font-display text-2xl font-bold text-white">Featured tour packages</h2>
                  <p className="mt-1 text-white/90">Popular itineraries available right now.</p>
                </div>
                <Link to="/packages" className="text-sm font-semibold text-white underline-offset-4 hover:underline">
                  View all packages &rarr;
                </Link>
              </div>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {featured.map((pkg) => (
                  <PackageCard key={pkg.id} tourPackage={pkg} />
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
