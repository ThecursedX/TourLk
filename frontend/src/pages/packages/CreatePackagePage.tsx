import { useNavigate } from 'react-router-dom'
import { createPackage } from '../../api/tourPackageApi'
import PackageForm from '../../components/packages/PackageForm'
import Card from '../../components/ui/Card'
import type { TourPackageRequestDto } from '../../types/tourPackage'

export default function CreatePackagePage() {
  const navigate = useNavigate()

  const handleSubmit = async (values: TourPackageRequestDto) => {
    const created = await createPackage(values)
    navigate(`/packages/${created.id}`)
  }

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-2xl">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">New Tour Package</h1>
        <PackageForm onSubmit={handleSubmit} submitLabel="Create Package" submittingLabel="Creating..." />
      </Card>
    </div>
  )
}
