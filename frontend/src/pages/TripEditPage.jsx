import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { errorMessage } from '../api/client.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import { updateTrip } from '../api/trips.js'
import useTrip from '../hooks/useTrip.js'
import AppHeader from '../components/AppHeader.jsx'
import TripForm from '../components/TripForm.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import DetailStatus from '../components/DetailStatus.jsx'
import { TRIP_STATUS_MESSAGE } from '../data/trips.js'
import { ArrowLeftIcon } from '../components/icons.jsx'
import '../styles/page.css'

/** 여행 정보 수정 화면 */
export default function TripEditPage() {
  const { tripId } = useParams()
  const { trip, status } = useTrip(tripId)

  if (status === REQUEST_STATUS.COMPLETE) {
    if (!trip.isOwner) return <Navigate to={`/trips/${trip.id}`} replace />
    return <TripEditView key={trip.id} trip={trip} />
  }

  return <DetailStatus {...TRIP_STATUS_MESSAGE[status]} backTo="/trips" backLabel="여행 목록으로" />
}

const UPDATE_ERROR = {
  TRIP_NOT_FOUND: '여행을 찾을 수 없어요. 이미 삭제되었을 수 있어요.',
}

/** 여행 수정 폼과 저장 확인 */
function TripEditView({ trip }) {
  const navigate = useNavigate()
  const detailPath = `/trips/${trip.id}`
  const [pendingEdit, setPendingEdit] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const closeConfirm = () => {
    setPendingEdit(null)
    setError('')
  }

  const confirmEdit = async () => {
    setBusy(true)
    setError('')
    try {
      await updateTrip(trip.id, pendingEdit)
      navigate(detailPath, { replace: true })
    } catch (err) {
      setError(errorMessage(err, UPDATE_ERROR, '여행 정보를 수정하지 못했어요.'))
      setBusy(false)
    }
  }

  return (
    <>
      <AppHeader />
      <main className="page">
        <div className="page-inner page-inner-narrow">
          <Link to={detailPath} className="back-link">
            <ArrowLeftIcon /> 여행 상세로
          </Link>
          <h1 className="page-title">여행 정보 수정</h1>

          <div className="trip-form-card">
            <TripForm
              initialTrip={trip}
              submitLabel="수정"
              onSubmit={setPendingEdit}
              onCancel={() => navigate(detailPath)}
            />
          </div>
        </div>
      </main>

      {pendingEdit && (
        <ConfirmDialog
          title="여행 정보를 수정할까요?"
          error={error}
          confirmLabel="수정"
          busyLabel="수정하는 중…"
          busy={busy}
          safe
          onConfirm={confirmEdit}
          onCancel={closeConfirm}
        >
          입력한 내용으로 여행 정보가 바뀌어요. 공개 범위는 그대로예요.
        </ConfirmDialog>
      )}
    </>
  )
}
