import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { fileUrl } from '../api/client.js'
import { CATEGORIES } from '../data/records.js'
import { RECORD_MEMO_MAX_LENGTH } from '../config/inputLimits.js'
import {
  ALLOWED_PHOTO_ACCEPT,
  MAX_PHOTO_SIZE_MB,
  MAX_PHOTO_TOTAL_MB,
  validatePhotoFiles,
} from '../config/uploadLimits.js'
import ConfirmDialog from './ConfirmDialog.jsx'
import PlaceSearchModal from './PlaceSearchModal.jsx'
import StarRatingInput from './StarRatingInput.jsx'
import RequiredNote from './RequiredNote.jsx'
import { CategoryIcon, CloseIcon, PhotoIcon, SearchIcon } from './icons.jsx'
import '../styles/form.css'
import './RecordForm.css'

const EMPTY = { place: null, category: '', rating: 0, memo: '', photos: [] }

/** 사진을 그릴 주소 */
const photoSrc = (photo) => photo.previewUrl ?? fileUrl(photo.url)

/** 기록 입력 폼 — 등록과 수정이 함께 쓴다 */
export default function RecordForm({
  initialValues = EMPTY,
  reference,
  submitLabel,
  submittingLabel,
  submitting = false,
  error,
  onSubmit,
  onCancel,
}) {
  const [place, setPlace] = useState(initialValues.place)
  const [category, setCategory] = useState(initialValues.category)
  const [rating, setRating] = useState(initialValues.rating)
  const [memo, setMemo] = useState(initialValues.memo)
  const [photos, setPhotos] = useState(initialValues.photos)
  const [errors, setErrors] = useState({})
  const [photoErrors, setPhotoErrors] = useState([])
  const [searchOpen, setSearchOpen] = useState(false)
  const [removingPhotoIndex, setRemovingPhotoIndex] = useState(null)

  // 폼이 사라질 때 미리보기 URL 을 해제한다
  const photosRef = useRef(photos)
  useEffect(() => {
    photosRef.current = photos
  }, [photos])
  useEffect(
    () => () => {
      photosRef.current.forEach((p) => p.previewUrl && URL.revokeObjectURL(p.previewUrl))
    },
    [],
  )

  const handlePhotoAdd = (e) => {
    const files = Array.from(e.target.files ?? [])
    const selectedBytes = photos.reduce((sum, p) => sum + (p.file?.size ?? 0), 0)
    const { accepted, errors: rejected } = validatePhotoFiles(files, selectedBytes)
    setPhotoErrors(rejected)
    if (accepted.length > 0) {
      setPhotos((prev) => [
        ...prev,
        ...accepted.map((file) => ({ file, previewUrl: URL.createObjectURL(file) })),
      ])
    }
    // 같은 파일을 다시 고를 수 있게 비운다
    e.target.value = ''
  }

  const removePhoto = (index) => {
    setPhotoErrors([])
    setPhotos((prev) => {
      const target = prev[index]
      if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl)
      return prev.filter((_, i) => i !== index)
    })
  }

  // 이미 올라간 사진은 확인을 거쳐 뺀다
  const handlePhotoRemoveClick = (index) => {
    if (photos[index].file) removePhoto(index)
    else setRemovingPhotoIndex(index)
  }

  const validate = () => {
    const next = {}
    if (!place) next.place = '장소를 검색해 선택해주세요.'
    if (!category) next.category = '카테고리를 선택해주세요.'
    if (!rating) next.rating = '평점을 선택해주세요.'
    if (memo.length > RECORD_MEMO_MAX_LENGTH) next.memo = `메모는 ${RECORD_MEMO_MAX_LENGTH}자 이하로 입력해주세요.`
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!validate()) return
    onSubmit({ place, category, rating, memo: memo.trim() || null, photos })
  }

  return (
    <>
      <form className="record-form" onSubmit={handleSubmit} noValidate>
        <RequiredNote />

        <div className="form-field">
          <label htmlFor="record-place-name" className="field-required">장소명</label>
          <div className="place-search-row">
            <input
              id="record-place-name"
              aria-required="true"
              type="text"
              value={place?.name ?? ''}
              placeholder="검색 버튼으로 장소를 선택해주세요"
              disabled
              readOnly
            />
            <button type="button" className="btn-secondary" onClick={() => setSearchOpen(true)}>
              <SearchIcon />
              검색
            </button>
          </div>
          {place?.address && <p className="field-hint">{place.address}</p>}
          {errors.place && <p className="field-error">{errors.place}</p>}
        </div>

        <div className="form-field">
          <span className="field-label field-required">카테고리</span>
          <div className="category-choice-group" role="radiogroup" aria-label="카테고리" aria-required="true">
            {CATEGORIES.map((c) => (
              <button
                key={c.key}
                type="button"
                role="radio"
                aria-checked={category === c.key}
                className={`category-choice${category === c.key ? ' active' : ''}`}
                onClick={() => setCategory(c.key)}
              >
                <CategoryIcon category={c.key} />
                {c.label}
              </button>
            ))}
          </div>
          {errors.category && <p className="field-error">{errors.category}</p>}
        </div>

        <div className="form-field">
          <span className="field-label field-required">평점</span>
          <StarRatingInput value={rating} onChange={setRating} />
          {errors.rating && <p className="field-error">{errors.rating}</p>}
        </div>

        <div className="form-field">
          <span className="field-label">사진</span>
          <div className="photo-upload-row">
            <input
              id="record-photos"
              className="photo-upload-input"
              type="file"
              accept={ALLOWED_PHOTO_ACCEPT}
              multiple
              onChange={handlePhotoAdd}
            />
            <label htmlFor="record-photos" className="btn-secondary photo-upload-btn">
              <PhotoIcon />
              사진 추가
            </label>
            <span className="photo-upload-hint">
              JPG·PNG·WEBP, 장당 {MAX_PHOTO_SIZE_MB}MB · 합계 {MAX_PHOTO_TOTAL_MB}MB 이하
            </span>
          </div>
          {photoErrors.map((message) => (
            <p className="field-error" key={message}>
              {message}
            </p>
          ))}
          {photos.length > 0 && (
            <ul className="photo-preview-list">
              {photos.map((photo, i) => (
                <li key={photo.id ?? photo.previewUrl} className="photo-preview-item">
                  <img src={photoSrc(photo)} alt={`사진 ${i + 1}`} />
                  <button type="button" onClick={() => handlePhotoRemoveClick(i)} aria-label={`사진 ${i + 1} 삭제`}>
                    <CloseIcon />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="record-memo">메모</label>
          <textarea
            id="record-memo"
            rows={4}
            value={memo}
            maxLength={RECORD_MEMO_MAX_LENGTH}
            onChange={(e) => setMemo(e.target.value)}
            placeholder="이 방문에 대한 메모를 남겨보세요"
          />
          {errors.memo && <p className="field-error">{errors.memo}</p>}
        </div>

        {error && <p className="field-error">{error}</p>}

        <div className="form-actions">
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={submitting}>
            취소
          </button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? submittingLabel : submitLabel}
          </button>
        </div>
      </form>

      {/* 모달은 body 에 포털로 그린다 */}
      {searchOpen &&
        createPortal(
          <PlaceSearchModal
            reference={reference}
            onClose={() => setSearchOpen(false)}
            onSelect={(selected) => {
              setPlace(selected)
              setSearchOpen(false)
              setErrors((prev) => ({ ...prev, place: undefined }))
            }}
          />,
          document.body,
        )}

      {removingPhotoIndex !== null &&
        createPortal(
          <ConfirmDialog
            title="이 사진을 삭제할까요?"
            confirmLabel="삭제"
            onConfirm={() => {
              removePhoto(removingPhotoIndex)
              setRemovingPhotoIndex(null)
            }}
            onCancel={() => setRemovingPhotoIndex(null)}
          >
            사진 {removingPhotoIndex + 1}번을 목록에서 빼요. 저장을 눌러야 실제로 지워지며, 취소하면
            되돌아와요.
          </ConfirmDialog>,
          document.body,
        )}
    </>
  )
}
