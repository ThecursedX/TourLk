import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { createPackage, replacePackageAddOns } from '../../api/tourPackageApi'
import PackageForm from '../../components/packages/PackageForm'
import Card from '../../components/ui/Card'
import type { ErrorResponse } from '../../types/auth'
import type { PackageAddOnItemDto, TourPackageRequestDto } from '../../types/tourPackage'

export default function CreatePackagePage() {
  const navigate = useNavigate()

  const handleSubmit = async (values: TourPackageRequestDto, addOns: PackageAddOnItemDto[]) => {
    const created = await createPackage(values)
    if (addOns.length > 0) {
      try {
        await replacePackageAddOns(created.id, addOns)
      } catch (err) {
        // The package exists now, so don't make the user resubmit (and duplicate) it: send them to edit it.
        const reason = isAxiosError<ErrorResponse>(err) ? err.response?.data?.message : undefined
        window.alert(`The package was created, but its add-ons could not be saved${reason ? `: ${reason}` : '.'}`)
        navigate(`/packages/${created.id}/edit`)
        return
      }
    }
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
