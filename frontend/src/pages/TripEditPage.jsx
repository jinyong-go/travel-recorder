import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import { updateTrip } from '../api/trips.js'
import useTrip from '../hooks/useTrip.js'
import TripForm from '../components/TripForm.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import '../styles/detailPage.css'
import '../styles/confirmPanel.css'
import '../styles/formPage.css'

/**
 * 여행 정보 수정 화면 (명세 §5.2). 만들기와 같은 폼을 쓰되 공개 범위는 싣지 않는다 —
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

  // 여행 상세와 같은 이유로, 볼 수 없는 여행과 없는 여행을 구분해 표시하지 않는다.
  const message =
    status === REQUEST_STATUS.LOADING
      ? { title: '여행을 불러오는 중이에요…', desc: '' }
      : status === REQUEST_STATUS.ERROR
        ? { title: '여행을 불러오지 못했습니다', desc: '잠시 후 다시 시도해주세요.' }
        : { title: '여행을 찾을 수 없습니다', desc: '존재하지 않거나 볼 수 없는 여행입니다.' }
  return (
    <main className="detail-page">
      <div className="detail-missing">
        <h1>{message.title}</h1>
        {message.desc && <p className="detail-missing-desc">{message.desc}</p>}
        <Link to="/trips" className="detail-missing-link">
          <ArrowLeftIcon /> 여행 목록으로
        </Link>
      </div>
    </main>
  )
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
      setError(err instanceof ApiError ? err.message : '여행 정보를 수정하지 못했습니다.')
      setBusy(false)
    }
  }

  return (
    <>
      <div className="register-page">
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
      </div>

      {pendingEdit && (
        <div className="modal-overlay" onClick={closeConfirm}>
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label="여행 정보 수정 확인"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>여행 정보를 수정할까요?</h2>
            {/* 공개 범위는 이 폼에 없다는 사실을 문구로 확인시킨다 (§9). */}
            <p className="confirm-desc">
              입력한 내용으로 여행 정보가 바뀝니다. 공개 범위는 그대로입니다.
            </p>
            {error && <p className="field-error">{error}</p>}
            <div className="confirm-actions">
              <button type="button" className="confirm-cancel" onClick={closeConfirm}>
                취소
              </button>
              <button
                type="button"
                className="confirm-ok confirm-ok-safe"
                disabled={busy}
                onClick={confirmEdit}
              >
                수정
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
