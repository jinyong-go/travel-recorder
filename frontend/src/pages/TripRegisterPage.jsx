import { useNavigate } from 'react-router-dom'
import { useRecords } from '../context/RecordsContext.jsx'
import TripForm from '../components/TripForm.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import './RegisterRecordPage.css'

export default function TripRegisterPage() {
  const navigate = useNavigate()
  const { createTrip, myGroups } = useRecords()

  const handleSubmit = (values) => {
    // TODO(백엔드 연동): POST /api/trips 호출로 대체.
    const trip = createTrip(values)
    navigate(`/trips/${trip.id}`)
  }

  return (
    <div className="register-page">
      <div className="register-page-inner">
        <div className="register-page-header">
          <button type="button" className="back-link" onClick={() => navigate('/trips')}>
            <ArrowLeftIcon /> 여행 목록으로
          </button>
          <h1>여행 만들기</h1>
        </div>

        <div className="trip-form-card">
          <TripForm
            submitLabel="여행 만들기"
            showVisibility
            groups={myGroups}
            onSubmit={handleSubmit}
            onCancel={() => navigate('/trips')}
            onCreateGroupClick={() => navigate('/groups')}
          />
        </div>
      </div>
    </div>
  )
}
