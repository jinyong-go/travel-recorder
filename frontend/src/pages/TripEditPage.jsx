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
import '../styles/formPage.css'

/**
 * 여행 정보 수정 화면. 만들기와 같은 폼을 쓰되 공개 범위는 싣지 않는다 —
 * 범위는 여행 상세의 전용 섹션에서만 바꾼다.
 *
 * 소유자가 아니면 상세로 돌려보낸다. 화면을 가릴 뿐이며 실제 거부는 서버가 한다.
 */
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

/** 폼 제출은 확인 모달을 여는 데서 끝나고, 확인을 누르면 저장한 뒤 상세로 돌아간다. */
function TripEditView({ trip }) {
  const navigate = useNavigate()
  const detailPath = `/trips/${trip.id}`
  // 확인을 누르기 전까지 수정 값을 들고만 있는다.
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
      // 뒤로가기로 저장한 폼에 돌아오지 않게 기록을 바꿔 끼운다.
      navigate(detailPath, { replace: true })
    } catch (err) {
      // 모달을 닫지 않는다. 입력값이 pendingEdit 에 남아 있어 그대로 다시 시도할 수 있다.
      setError(errorMessage(err, UPDATE_ERROR, '여행 정보를 수정하지 못했어요.'))
      setBusy(false)
    }
  }

  return (
    <>
      <AppHeader />
      <main className="register-page">
        <div className="register-page-inner">
          <div className="register-page-header">
            <Link to={detailPath} className="back-link">
              <ArrowLeftIcon /> 여행 상세로
            </Link>
            <h1>여행 정보 수정</h1>
          </div>

          <div className="trip-form-card">
            {/* 공개 범위는 이 폼에 없다. 여행 상세의 전용 섹션에서만 바꾼다. */}
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
          {/* 공개 범위는 이 폼에 없다는 사실을 문구로 확인시킨다. */}
          입력한 내용으로 여행 정보가 바뀌어요. 공개 범위는 그대로예요.
        </ConfirmDialog>
      )}
    </>
  )
}
