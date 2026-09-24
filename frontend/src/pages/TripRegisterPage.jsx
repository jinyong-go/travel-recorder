import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client.js'
import { createTrip } from '../api/trips.js'
import TripForm from '../components/TripForm.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import './RegisterRecordPage.css'

export default function TripRegisterPage() {
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  /** 만든 여행 상세로 보낸다 (명세 §5.1). 실패하면 입력값은 폼에 그대로 남는다. */
  const handleSubmit = async (values) => {
    setSubmitting(true)
    setError('')
    try {
      const trip = await createTrip(values)
      navigate(`/trips/${trip.id}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '여행을 만들지 못했습니다.')
      setSubmitting(false)
    }
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
            submitting={submitting}
            onSubmit={handleSubmit}
            onCancel={() => navigate('/trips')}
            onCreateGroupClick={() => navigate('/groups')}
          />
          {error && <p className="field-error">{error}</p>}
        </div>
      </div>
    </div>
  )
}
