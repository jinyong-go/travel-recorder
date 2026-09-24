import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client.js'
import { createRecord, uploadPhotos } from '../api/records.js'
import { CATEGORIES, MEMO_MAX_LENGTH, categoryIcon, visibilityMeta } from '../data/records.js'
import {
  ALLOWED_PHOTO_ACCEPT,
  MAX_PHOTO_SIZE_MB,
  MAX_PHOTO_TOTAL_MB,
  validatePhotoFiles,
} from '../config/uploadLimits.js'
import PlaceSearchModal from './PlaceSearchModal.jsx'
import StarRatingInput from './StarRatingInput.jsx'
import { CloseIcon, PhotoIcon, SearchIcon } from './icons.jsx'
import '../pages/RegisterRecordPage.css'
import './RecordRegisterModal.css'

const SELECTABLE_CATEGORIES = CATEGORIES.filter((c) => c.key !== 'all')


const REFERENCE_LOCATION_LABEL = {
  locating: '현재 위치 확인 중...',
  geo: '현재 위치 사용 중',
  manual: '직접 지정한 위치 사용 중',
  default: '기본 위치(서울역) 사용 중',
}

/**
 * 여행 상세에서 여는 기록 등록 모달 (명세 §5.3).
 *
 * 소속 여행은 지금 보고 있는 여행으로 정해져 있어 고르는 단계가 없다. 여는 쪽(여행 상세)이
 * 소유자에게만 버튼을 보여주므로 `trip.visibility` 가 채워져 있다.
 *
 * 기준 위치(`reference`)는 여는 쪽이 가진 `useReferenceLocation()` 결과를 그대로 받는다. 따로
 * 부르면 상태가 둘로 갈라져, 여기서 위치를 직접 지정해도 뒤의 여행 상세 거리가 바뀌지 않는다.
 *
 * 제출은 기록 생성 → 사진 업로드 두 단계다 (공통 명세 §6.2). 사진만 실패하면 기록을 되돌리지
 * 않고 `onCreated(record, { photoFailed: true })` 로 알린다 — 기록 자체는 유효하기 때문이다.
 */
export default function RecordRegisterModal({ trip, reference, onClose, onCreated }) {
  const { location: referenceLocation, source, status, setManualLocation } = reference

  const [category, setCategory] = useState('')
  const [selectedPlace, setSelectedPlace] = useState(null)
  const [rating, setRating] = useState(0)
  const [memo, setMemo] = useState('')
  const [photos, setPhotos] = useState([])
  const [searchOpen, setSearchOpen] = useState(false)
  const [manualLocationOpen, setManualLocationOpen] = useState(false)
  const [photoErrors, setPhotoErrors] = useState([])
  const [errors, setErrors] = useState({})
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // 모달이 닫힐 때 미리보기 URL 을 해제한다. 마지막 목록을 알아야 하므로 ref 로 들고 있는다.
  const photosRef = useRef(photos)
  useEffect(() => {
    photosRef.current = photos
  }, [photos])
  useEffect(
    () => () => {
      photosRef.current.forEach((p) => URL.revokeObjectURL(p.previewUrl))
    },
    [],
  )

  const referenceStatusLabel =
    status === 'locating' ? REFERENCE_LOCATION_LABEL.locating : REFERENCE_LOCATION_LABEL[source]

  const handlePhotoChange = (e) => {
    const files = Array.from(e.target.files ?? [])
    const selectedBytes = photos.reduce((sum, p) => sum + p.file.size, 0)
    const { accepted, errors: rejected } = validatePhotoFiles(files, selectedBytes)
    const next = accepted.map((file) => ({ file, previewUrl: URL.createObjectURL(file) }))
    if (next.length > 0) setPhotos((prev) => [...prev, ...next])
    setPhotoErrors(rejected)
    e.target.value = ''
  }

  const removePhoto = (idx) => {
    setPhotoErrors([])
    setPhotos((prev) => {
      const target = prev[idx]
      if (target) URL.revokeObjectURL(target.previewUrl)
      return prev.filter((_, i) => i !== idx)
    })
  }

  const validate = () => {
    const next = {}
    if (!selectedPlace) next.place = '장소를 검색해 선택해주세요.'
    if (!category) next.category = '카테고리를 선택해주세요.'
    if (!rating) next.rating = '평점을 선택해주세요.'
    if (memo.length > MEMO_MAX_LENGTH) next.memo = `메모는 ${MEMO_MAX_LENGTH}자 이하로 입력해주세요.`
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    setSubmitError('')

    // 1단계: 기록 생성. 실패하면 모달을 닫지 않고 입력값을 그대로 둔다 (명세 §5.3).
    let record
    try {
      record = await createRecord({
        tripId: trip.id,
        name: selectedPlace.name,
        category,
        address: selectedPlace.address,
        roadAddress: selectedPlace.roadAddress ?? null,
        externalLink: selectedPlace.link ?? null,
        latitude: selectedPlace.location.lat,
        longitude: selectedPlace.location.lng,
        rating,
        memo: memo.trim() || null,
      })
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : '여행지를 등록하지 못했습니다.')
      setSubmitting(false)
      return
    }

    // 2단계: 사진 업로드. 실패해도 기록은 되돌리지 않는다. 사진 없는 기록도 유효하다 (공통 명세 §6.2).
    let photoFailed = false
    if (photos.length > 0) {
      try {
        await uploadPhotos(
          record.id,
          photos.map((p) => p.file),
        )
      } catch {
        photoFailed = true
      }
    }
    onCreated(record, { photoFailed })
  }

  const visibility = visibilityMeta(trip.visibility)

  return (
    <>
      {/* 제출 중에는 바깥 클릭으로 닫지 않는다. 기록이 만들어진 뒤 사진을 올리는 도중일 수 있다. */}
      <div className="modal-overlay" onClick={submitting ? undefined : onClose}>
        <div
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
          {/* 공개 여부를 모른 채 저장하지 않도록 이 여행의 범위를 먼저 보여준다 (명세 §5.3, §9). */}
          <p className="preset-trip">
            {trip.name} 여행에 추가합니다. 이 기록은 여행의 공개 범위({visibility.label})를 따릅니다.
          </p>

          <form className="register-form" onSubmit={handleSubmit} noValidate>
            <div className="form-field">
              <label htmlFor="place-name">장소명</label>
              <div className="place-search-row">
                <input
                  id="place-name"
                  type="text"
                  value={selectedPlace?.name ?? ''}
                  placeholder="검색 버튼으로 장소를 선택해주세요"
                  disabled
                  readOnly
                />
                <button
                  type="button"
                  className="search-trigger-btn"
                  onClick={() => setSearchOpen(true)}
                >
                  <SearchIcon />
                  검색
                </button>
              </div>
              {errors.place && <p className="field-error">{errors.place}</p>}
            </div>

            {selectedPlace && (
              <div className="form-field">
                <span className="field-label">위치</span>
                <p className="selected-address">{selectedPlace.address}</p>
              </div>
            )}

            <div className="form-field">
              <span className="field-label">카테고리</span>
              <div className="category-choice-group" role="radiogroup" aria-label="카테고리">
                {SELECTABLE_CATEGORIES.map((c) => (
                  <button
                    key={c.key}
                    type="button"
                    role="radio"
                    aria-checked={category === c.key}
                    className={`category-choice${category === c.key ? ' active' : ''}`}
                    onClick={() => setCategory(c.key)}
                  >
                    {categoryIcon(c.key)} {c.label}
                  </button>
                ))}
              </div>
              {errors.category && <p className="field-error">{errors.category}</p>}
            </div>

            <div className="form-field">
              <span className="field-label">평점</span>
              <StarRatingInput value={rating} onChange={setRating} />
              {errors.rating && <p className="field-error">{errors.rating}</p>}
            </div>

            <div className="form-field">
              <span className="field-label">사진</span>
              <div className="photo-upload-row">
                <input
                  id="photos"
                  className="photo-upload-input"
                  type="file"
                  accept={ALLOWED_PHOTO_ACCEPT}
                  multiple
                  onChange={handlePhotoChange}
                />
                <label htmlFor="photos" className="photo-upload-btn">
                  <PhotoIcon />
                  사진 선택
                </label>
                <span className="photo-upload-hint">
                  {photos.length > 0
                    ? `${photos.length}장 선택됨`
                    : `JPG · PNG · WEBP, 한 장당 ${MAX_PHOTO_SIZE_MB}MB · 합계 ${MAX_PHOTO_TOTAL_MB}MB 이하`}
                </span>
              </div>
              {photoErrors.map((message) => (
                <p className="field-error" key={message}>
                  {message}
                </p>
              ))}
              {photos.length > 0 && (
                <ul className="photo-preview-list">
                  {photos.map((p, idx) => (
                    <li key={p.previewUrl} className="photo-preview-item">
                      <img src={p.previewUrl} alt="" />
                      <button type="button" onClick={() => removePhoto(idx)} aria-label="사진 삭제">
                        <CloseIcon />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="form-field">
              <label htmlFor="memo">메모</label>
              <textarea
                id="memo"
                rows={4}
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                placeholder="이 방문에 대한 메모를 남겨보세요"
              />
              {errors.memo && <p className="field-error">{errors.memo}</p>}
            </div>

            <div className="reference-location-note">
              <span>기준 위치: {referenceStatusLabel}</span>
              <button
                type="button"
                className="link-btn"
                onClick={() => setManualLocationOpen(true)}
              >
                위치 직접 지정
              </button>
            </div>

            {submitError && <p className="field-error">{submitError}</p>}

            <div className="form-actions">
              <button type="button" className="btn-secondary" onClick={onClose} disabled={submitting}>
                취소
              </button>
              <button type="submit" className="btn-primary" disabled={submitting}>
                {submitting ? '등록 중...' : '등록하기'}
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* 검색 모달은 등록 모달의 형제로 그린다. 안에 두면 검색 모달의 클릭이 등록 모달의
          바깥 클릭으로 전달되어 등록 모달까지 닫힌다. */}
      {searchOpen && (
        <PlaceSearchModal
          referenceLocation={referenceLocation}
          onClose={() => setSearchOpen(false)}
          onSelect={(place) => {
            setSelectedPlace(place)
            setSearchOpen(false)
            setErrors((prev) => ({ ...prev, place: undefined }))
          }}
        />
      )}

      {manualLocationOpen && (
        <PlaceSearchModal
          referenceLocation={referenceLocation}
          title="내 위치 지정"
          onClose={() => setManualLocationOpen(false)}
          onSelect={(place) => {
            setManualLocation(place.location)
            setManualLocationOpen(false)
          }}
        />
      )}
    </>
  )
}
