import { useNavigate } from 'react-router-dom'
import { createAccommodation } from '../../api/accommodationApi'
import AccommodationForm from '../../components/accommodations/AccommodationForm'
import Card from '../../components/ui/Card'
import type { AccommodationRequestDto } from '../../types/accommodation'

export default function CreateAccommodationPage() {
  const navigate = useNavigate()

  const handleSubmit = async (values: AccommodationRequestDto) => {
    const created = await createAccommodation(values)
    navigate(`/accommodations/${created.id}/edit`)
  }

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-2xl">
        <h1 className="mb-2 text-xl font-semibold text-slate-900">New Property</h1>
        <p className="mb-6 text-sm text-slate-600">
          You'll be able to add room types once the property is created.
        </p>
        <AccommodationForm onSubmit={handleSubmit} submitLabel="Create Property" submittingLabel="Creating..." />
      </Card>
    </div>
  )
}
