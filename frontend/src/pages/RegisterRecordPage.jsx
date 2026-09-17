import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CATEGORIES, DEFAULT_VISIBILITY, categoryIcon } from '../data/records.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useReferenceLocation from '../hooks/useReferenceLocation.js'
import { haversineDistanceKm } from '../utils/geo.js'
import {
  ALLOWED_PHOTO_ACCEPT,
  MAX_PHOTO_SIZE_MB,
  MAX_PHOTO_TOTAL_MB,
  validatePhotoFiles,
} from '../config/uploadLimits.js'
import PlaceSearchModal from '../components/PlaceSearchModal.jsx'
import StarRatingInput from '../components/StarRatingInput.jsx'
import VisibilitySelect from '../components/VisibilitySelect.jsx'
import { ArrowLeftIcon, CloseIcon, PhotoIcon, SearchIcon } from '../components/icons.jsx'
import './RegisterRecordPage.css'

const SELECTABLE_CATEGORIES = CATEGORIES.filter((c) => c.key !== 'all')

const REFERENCE_LOCATION_LABEL = {
  locating: '현재 위치 확인 중...',
  geo: '현재 위치 사용 중',
  manual: '직접 지정한 위치 사용 중',
  default: '기본 위치(서울역) 사용 중',
}

export default function RegisterRecordPage() {
  const navigate = useNavigate()
  const { addRecord, myGroups } = useRecords()
  const { location: referenceLocation, source, status, setManualLocation } = useReferenceLocation()

  const [category, setCategory] = useState('')
  const [selectedPlace, setSelectedPlace] = useState(null)
  const [rating, setRating] = useState(0)
  const [memo, setMemo] = useState('')
  const [photos, setPhotos] = useState([])
  // 기본값은 언제나 "나만 보기" 다. 공개는 사용자가 직접 고른 결과여야 한다.
  const [visibility, setVisibility] = useState(DEFAULT_VISIBILITY)
  const [sharedGroupIds, setSharedGroupIds] = useState([])
  const [searchOpen, setSearchOpen] = useState(false)
  const [manualLocationOpen, setManualLocationOpen] = useState(false)
  const [photoErrors, setPhotoErrors] = useState([])
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

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
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    // TODO(백엔드 연동): POST /api/records → POST /api/records/{id}/photos 2단계 호출로 대체.
    // 기록 생성이 성공하고 사진 업로드만 실패하면 사진 없는 기록이 남으므로,
    // 그때는 되돌리지 말고 상세 화면으로 보낸 뒤 재업로드를 안내해야 한다.
    window.setTimeout(() => {
      const distanceKm = haversineDistanceKm(referenceLocation, selectedPlace.location)
      const today = new Date().toISOString().slice(0, 10)
      addRecord({
        id: `local-${Date.now()}`,
        name: selectedPlace.name,
        externalLink: selectedPlace.link,
        category,
        region: selectedPlace.region ?? selectedPlace.address,
        address: selectedPlace.address,
        rating,
        distanceKm,
        createdAt: today,
        updatedAt: today,
        memo: memo.trim(),
        photos: photos.map((p) => p.previewUrl),
        location: selectedPlace.location,
        visibility,
        sharedGroupIds: visibility === 'GROUP' ? sharedGroupIds : [],
      })
      navigate('/records')
    }, 500)
  }

  return (
    <div className="register-page">
      <div className="register-page-inner">
        <div className="register-page-header">
          <button type="button" className="back-link" onClick={() => navigate('/records')}>
            <ArrowLeftIcon /> 목록으로
          </button>
          <h1>기록 남기기</h1>
        </div>

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
          </div>

          <div className="form-field">
            <VisibilitySelect
              value={visibility}
              onChange={setVisibility}
              groups={myGroups}
              selectedGroupIds={sharedGroupIds}
              onChangeGroups={setSharedGroupIds}
              onCreateGroupClick={() => navigate('/groups')}
            />
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

          <div className="form-actions">
            <button type="button" className="btn-secondary" onClick={() => navigate('/records')}>
              취소
            </button>
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? '등록 중...' : '등록하기'}
            </button>
          </div>
        </form>
      </div>

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
    </div>
  )
}
