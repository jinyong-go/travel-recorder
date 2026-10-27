import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client.js'
import { createTrip } from '../api/trips.js'
import AppHeader from '../components/AppHeader.jsx'
import TripForm from '../components/TripForm.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import '../styles/page.css'

const CREATE_ERROR = {
  GROUP_NOT_FOUND: '공유할 수 없는 그룹이 선택되어 있어요. 그룹 선택을 다시 확인해주세요.',
}

/** 여행 만들기 화면. 공개 범위까지 함께 받아 한 번에 만든다. */
export default function TripRegisterPage() {
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  /** 만든 여행 상세로 보낸다. 실패하면 입력값은 폼에 그대로 남는다. */
  const handleSubmit = async (values) => {
    setSubmitting(true)
    setError('')
    try {
      const trip = await createTrip(values)
      navigate(`/trips/${trip.id}`)
    } catch (err) {
      setError(errorMessage(err, CREATE_ERROR, '여행을 만들지 못했어요.'))
      setSubmitting(false)
    }
  }

  return (
    <>
      <AppHeader />
      <main className="page">
        <div className="page-inner page-inner-narrow">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>
          <h1 className="page-title">여행 만들기</h1>

          <div className="trip-form-card">
            <TripForm
              submitLabel="여행 만들기"
              submittingLabel="만드는 중…"
              showVisibility
              submitting={submitting}
              onSubmit={handleSubmit}
              onCancel={() => navigate('/trips')}
              onCreateGroupClick={() => navigate('/groups')}
            />
            {error && <p className="field-error">{error}</p>}
          </div>
        </div>
      </main>
    </>
  )
}
