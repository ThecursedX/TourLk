import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getPackageById, updatePackage } from '../../api/tourPackageApi'
import PackageForm from '../../components/packages/PackageForm'
import Card from '../../components/ui/Card'
import type { TourPackageRequestDto } from '../../types/tourPackage'
import type { DestinationSummary } from '../../types/destination'

export default function EditPackagePage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [initialValues, setInitialValues] = useState<TourPackageRequestDto | null>(null)
  const [currentDestination, setCurrentDestination] = useState<DestinationSummary | null>(null)
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
        })
        setCurrentDestination(pkg.destination)
      })
      .catch(() => setError('Could not load this tour package. Please try again later.'))
      .finally(() => setLoading(false))
  }, [id])

  const handleSubmit = async (values: TourPackageRequestDto) => {
    if (!id) return
    await updatePackage(Number(id), values)
    navigate(`/packages/${id}`)
  }

  if (loading) return <p className="text-slate-600">Loading...</p>
  if (error) return <p className="text-red-600">{error}</p>
  if (!initialValues) return null

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-2xl">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Edit Tour Package</h1>
        <PackageForm
          initialValues={initialValues}
          currentDestination={currentDestination}
          onSubmit={handleSubmit}
          submitLabel="Save Changes"
          submittingLabel="Saving..."
        />
      </Card>
    </div>
  )
}
