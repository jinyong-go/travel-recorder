import { useState } from 'react'
import { errorMessage } from '../api/client.js'
import { createRecord, uploadPhotos } from '../api/records.js'
import { recordPayload } from '../data/records.js'
import { visibilityMeta } from '../data/visibility.js'
import { tripVisibility } from '../data/trips.js'
import useModal from '../hooks/useModal.js'
import RecordForm from './RecordForm.jsx'
import { CloseIcon } from './icons.jsx'
import './RecordRegisterModal.css'

const CREATE_ERROR = {
  TRIP_NOT_FOUND: '여행을 찾을 수 없습니다. 이미 삭제되었을 수 있어요.',
}

/**
 * 여행 상세에서 여는 기록 등록 모달.
 *
 * 소속 여행은 지금 보고 있는 여행으로 정해져 있어 고르는 단계가 없다. 여는 쪽(여행 상세)이
 * 소유자에게만 버튼을 보여주므로 `trip.visibility` 가 채워져 있다. `reference` 는 여는 쪽의 기준
 * 위치로, 폼의 장소 검색에 그대로 넘긴다.
 *
 * 제출은 기록 생성 → 사진 업로드 두 단계다. 사진만 실패하면 기록을 되돌리지
 * 않고 `onCreated(record, { photoFailed: true })` 로 알린다 — 기록 자체는 유효하기 때문이다.
 */
export default function RecordRegisterModal({ trip, reference, onClose, onCreated }) {
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // 바깥 클릭과 같은 이유로 제출 중에는 Esc 로도 닫지 않는다.
  const panelRef = useModal(submitting ? undefined : onClose)

  const handleSubmit = async (values) => {
    setSubmitting(true)
    setSubmitError('')

    // 1단계: 기록 생성. 실패하면 모달을 닫지 않고 입력값을 그대로 둔다.
    let record
    try {
      record = await createRecord({ tripId: trip.id, ...recordPayload(values) })
    } catch (err) {
      setSubmitError(errorMessage(err, CREATE_ERROR, '여행지를 등록하지 못했습니다.'))
      setSubmitting(false)
      return
    }

    // 2단계: 사진 업로드. 실패해도 기록은 되돌리지 않는다. 사진 없는 기록도 유효하다.
    let photoFailed = false
    if (values.photos.length > 0) {
      try {
        await uploadPhotos(
          record.id,
          values.photos.map((p) => p.file),
        )
      } catch {
        photoFailed = true
      }
    }
    onCreated(record, { photoFailed })
  }

  const visibility = visibilityMeta(tripVisibility(trip))

  return (
    // 제출 중에는 바깥 클릭으로 닫지 않는다. 기록이 만들어진 뒤 사진을 올리는 도중일 수 있다.
    <div className="modal-overlay" onClick={submitting ? undefined : onClose}>
      <div
        ref={panelRef}
        className="modal-panel record-register-panel"
        role="dialog"
        aria-modal="true"
        aria-label="여행지 남기기"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          className="modal-close"
          onClick={onClose}
          disabled={submitting}
          aria-label="닫기"
        >
          <CloseIcon />
        </button>
        <h2>여행지 남기기</h2>
        {/* 공개 여부를 모른 채 저장하지 않도록 이 여행의 범위를 먼저 보여준다. */}
        <p className="preset-trip">
          {trip.name} 여행에 추가합니다. 이 기록은 여행의 공개 범위({visibility.label})를 따릅니다.
        </p>

        <RecordForm
          reference={reference}
          submitLabel="등록하기"
          submittingLabel="등록 중…"
          submitting={submitting}
          error={submitError}
          onSubmit={handleSubmit}
          onCancel={onClose}
        />
      </div>
    </div>
  )
}
