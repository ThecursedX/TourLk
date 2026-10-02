import { osmEmbedUrl, osmLinkUrl } from '../../types/destination'

interface DestinationMapProps {
  name: string
  latitude: number
  longitude: number
}

/** OpenStreetMap embedded in an iframe (no library or API key), plus a link to open the full map. */
export default function DestinationMap({ name, latitude, longitude }: DestinationMapProps) {
  return (
    <div className="flex flex-col gap-2">
      <iframe
        title={`Map of ${name}`}
        src={osmEmbedUrl(latitude, longitude)}
        className="h-72 w-full rounded-xl border border-slate-200"
        loading="lazy"
        referrerPolicy="no-referrer"
      />
      <a
        href={osmLinkUrl(latitude, longitude)}
        target="_blank"
        rel="noopener noreferrer"
        className="text-sm font-medium text-blue-600 hover:underline"
      >
        View larger map on OpenStreetMap &rarr;
      </a>
    </div>
  )
}
