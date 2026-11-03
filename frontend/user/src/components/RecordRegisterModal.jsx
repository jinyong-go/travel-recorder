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
  TRIP_NOT_FOUND: '여행을 찾을 수 없어요. 이미 삭제되었을 수 있어요.',
}

/** 여행에 기록을 추가하는 등록 모달 */
export default function RecordRegisterModal({ trip, reference, onClose, onCreated }) {
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // 제출 중에는 닫지 않는다
  const panelRef = useModal(submitting ? undefined : onClose)

  const handleSubmit = async (values) => {
    setSubmitting(true)
    setSubmitError('')

    // 기록 생성
    let record
    try {
      record = await createRecord({ tripId: trip.id, ...recordPayload(values) })
    } catch (err) {
      setSubmitError(errorMessage(err, CREATE_ERROR, '여행지를 등록하지 못했어요.'))
      setSubmitting(false)
      return
    }

    // 사진 업로드 — 실패해도 기록은 남긴다
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
        {/* 이 여행의 공개 범위 */}
        <p className="preset-trip">
          {trip.name} 여행에 추가해요. 이 기록은 여행의 공개 범위({visibility.label})를 따라요.
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
