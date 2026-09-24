import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { CATEGORIES, categoryIcon } from '../data/records.js'
import { fetchTrips } from '../api/trips.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useTrip from '../hooks/useTrip.js'
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
  const [searchParams] = useSearchParams()
  const { addRecord } = useRecords()
  const { location: referenceLocation, source, status, setManualLocation } = useReferenceLocation()

  // 소속 여행을 먼저 정해야 기록을 만들 수 있다. 여행 없는 기록은 존재할 수 없다 (공통 명세 §3.3).
  // ?tripId= 로 들어와도 본인 여행으로 확인된 뒤에만 정해진 것으로 본다 (명세 §5.3).
  const tripIdParam = searchParams.get('tripId')
  const { trip: paramTrip, status: paramTripStatus } = useTrip(tripIdParam)
  const presetTrip = paramTrip?.isOwner ? paramTrip : null
  const [pickedTripId, setPickedTripId] = useState('')
  const tripId = presetTrip ? String(presetTrip.id) : pickedTripId

  // 선택지는 최근 만든 내 여행 한 페이지다. 전부 받아 오지 않는다 (명세 §5.3).
  // null 은 아직 받지 못한 상태, 'error' 는 실패다.
  const [myTripsPage, setMyTripsPage] = useState(null)
  useEffect(() => {
    let cancelled = false
    fetchTrips('mine', 'recent', 0)
      .then((result) => {
        if (!cancelled) setMyTripsPage(result)
      })
      .catch(() => {
        if (!cancelled) setMyTripsPage('error')
      })
    return () => {
      cancelled = true
    }
  }, [])
  const myTrips = myTripsPage?.content ?? []
  const hasMoreTrips = myTrips.length > 0 && myTripsPage.totalElements > myTrips.length

  const [category, setCategory] = useState('')
  const [selectedPlace, setSelectedPlace] = useState(null)
  const [rating, setRating] = useState(0)
  const [memo, setMemo] = useState('')
  const [photos, setPhotos] = useState([])
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
    if (!tripId) next.trip = '이 기록을 담을 여행을 선택해주세요.'
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
        tripId: Number(tripId),
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
      })
      navigate(`/trips/${tripId}`)
    }, 500)
  }

  return (
    <div className="register-page">
      <div className="register-page-inner">
        <div className="register-page-header">
          <button
            type="button"
            className="back-link"
            onClick={() => navigate(tripId ? `/trips/${tripId}` : '/trips')}
          >
            <ArrowLeftIcon /> 돌아가기
          </button>
          <h1>여행지 남기기</h1>
        </div>

        <form className="register-form" onSubmit={handleSubmit} noValidate>
          {/* 공개 범위는 묻지 않는다. 소속 여행에서 이미 정해졌다 (공통 명세 §2.3). */}
          <div className="form-field">
            <label htmlFor="trip-select">소속 여행</label>
            {presetTrip ? (
              // 여행 상세에서 들어온 경로다. 대상이 이미 정해져 있으므로 바꾸는 수단을 두지 않는다.
              <p className="preset-trip">{presetTrip.name} 여행에 추가합니다</p>
            ) : paramTripStatus === 'loading' || myTripsPage === null ? (
              <p className="field-hint">여행을 불러오는 중이에요…</p>
            ) : myTripsPage === 'error' ? (
              <p className="field-error">여행을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</p>
            ) : myTrips.length === 0 ? (
              <p className="field-hint">
                아직 여행이 없습니다. <Link to="/trips/new">여행을 먼저 만들어</Link> 주세요.
              </p>
            ) : (
              <select
                id="trip-select"
                value={tripId}
                onChange={(e) => setPickedTripId(e.target.value)}
              >
                <option value="">여행을 선택해주세요</option>
                {myTrips.map((trip) => (
                  <option key={trip.id} value={trip.id}>
                    {trip.name}
                  </option>
                ))}
              </select>
            )}
            {!presetTrip && hasMoreTrips && (
              <p className="field-hint">
                찾는 여행이 없으면 그 여행 화면의 &lsquo;여행지 추가&rsquo;로 들어와 주세요.
              </p>
            )}
            {errors.trip && <p className="field-error">{errors.trip}</p>}
            <p className="field-hint">이 기록의 공개 범위는 소속 여행을 따릅니다.</p>
          </div>

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
