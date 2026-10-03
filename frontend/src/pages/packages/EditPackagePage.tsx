import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { getPackageById, replacePackageAddOns, updatePackage } from '../../api/tourPackageApi'
import PackageForm from '../../components/packages/PackageForm'
import Card from '../../components/ui/Card'
import type { ErrorResponse } from '../../types/auth'
import type { PackageAddOnItemDto, TourPackageRequestDto } from '../../types/tourPackage'
import type { DestinationSummary } from '../../types/destination'

export default function EditPackagePage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [initialValues, setInitialValues] = useState<TourPackageRequestDto | null>(null)
  const [currentDestination, setCurrentDestination] = useState<DestinationSummary | null>(null)
  const [initialAddOns, setInitialAddOns] = useState<PackageAddOnItemDto[]>([])
  const [rejectionReason, setRejectionReason] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    getPackageById(Number(id))
      .then((pkg) => {
        setInitialValues({
          title: pkg.title,
          description: pkg.description,
          destinationId: pkg.destination.id,
          durationDays: pkg.durationDays,
          price: pkg.price,
          maxCapacity: pkg.maxCapacity,
          itineraryDays: pkg.itineraryDays.map((day) => ({
            dayNumber: day.dayNumber,
            title: day.title,
            description: day.description ?? '',
            placesToVisit: day.placesToVisit,
          })),
          inclusions: pkg.inclusions,
          exclusions: pkg.exclusions,
          imageUrls: pkg.imageUrls,
        })
        setInitialAddOns(
          (pkg.addOns ?? []).map((a) =>
            a.room
              ? { roomId: a.room.id, note: a.note ?? undefined }
              : { vehicleId: a.vehicle?.id, note: a.note ?? undefined },
          ),
        )
        setCurrentDestination(pkg.destination)
        setRejectionReason(pkg.status === 'DRAFT' ? pkg.rejectionReason : null)
      })
      .catch(() => setError('Could not load this tour package. Please try again later.'))
      .finally(() => setLoading(false))
  }, [id])

  /**
   * Editing price/duration/capacity of an ACTIVE package with upcoming
   * bookings comes back as 409 CONFIRMATION_REQUIRED; ask, then retry with
   * confirmChanges. Cancelling just leaves the form as it is. Any other
   * error is rethrown for PackageForm to display.
   */
  const handleSubmit = async (values: TourPackageRequestDto, addOns: PackageAddOnItemDto[]) => {
    if (!id) return
    try {
      await updatePackage(Number(id), values)
    } catch (err) {
      if (!isAxiosError<ErrorResponse>(err) || err.response?.data?.code !== 'CONFIRMATION_REQUIRED') {
        throw err
      }
      const confirmed = window.confirm(
        `${err.response.data.message}

Save these changes anyway?`,
      )
      if (!confirmed) return
      await updatePackage(Number(id), values, true)
    }
    await replacePackageAddOns(Number(id), addOns)
    navigate(`/packages/${id}`)
  }

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!initialValues) return null

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-2xl">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Edit Tour Package</h1>
        {rejectionReason && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            <p className="font-semibold">This package was rejected</p>
            <p className="mt-1 whitespace-pre-line">{rejectionReason}</p>
            <p className="mt-2 text-xs text-red-700">
              Address this, save, then submit it for approval again from My Packages.
            </p>
          </div>
        )}
        <PackageForm
          initialValues={initialValues}
          currentDestination={currentDestination}
          packageId={Number(id)}
          initialAddOns={initialAddOns}
          onSubmit={handleSubmit}
          submitLabel="Save Changes"
          submittingLabel="Saving..."
        />
      </Card>
    </div>
  )
}
