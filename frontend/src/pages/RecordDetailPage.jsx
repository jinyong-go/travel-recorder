import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, fileUrl } from '../api/client.js'
import * as recordApi from '../api/records.js'
import {
  CATEGORIES,
  MEMO_MAX_LENGTH,
  categoryIcon,
  categoryLabel,
  dateLabel,
  placeOf,
} from '../data/records.js'
import useLatestRequest from '../hooks/useLatestRequest.js'
import useTrip from '../hooks/useTrip.js'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import {
  ALLOWED_PHOTO_ACCEPT,
  MAX_PHOTO_SIZE_MB,
  MAX_PHOTO_TOTAL_MB,
  validatePhotoFiles,
} from '../config/uploadLimits.js'
import { haversineDistanceKm } from '../utils/geo.js'
import useMapMode from '../hooks/useMapMode.js'
import useReferenceLocation from '../hooks/useReferenceLocation.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import StarRatingDisplay from '../components/StarRatingDisplay.jsx'
import StarRatingInput from '../components/StarRatingInput.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import PlaceSearchModal from '../components/PlaceSearchModal.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import {
  ArrowLeftIcon,
  CloseIcon,
  MapPinIcon,
  MapViewIcon,
  PhotoIcon,
  SearchIcon,
} from '../components/icons.jsx'
// 수정 폼의 입력 요소(장소 검색 줄·카테고리 칩·파일 선택)는 등록 폼과 같은 생김새를 쓴다.
import './RegisterRecordPage.css'
import './RecordDetailPage.css'

const SELECTABLE_CATEGORIES = CATEGORIES.filter((c) => c.key !== 'all')

/** 수정 초안의 사진 하나를 그릴 주소. 이미 올라간 사진은 서버 URL, 새로 고른 파일은 미리보기다. */
const photoSrc = (photo) => photo.previewUrl ?? fileUrl(photo.url)

export default function RecordDetailPage() {
  const { recordId } = useParams()
  const navigate = useNavigate()
  const { isEmbed } = useMapMode()
  const { location: referenceLocation } = useReferenceLocation()

  // 'loading' | 'ready' | 'missing' | 'error'. 404 는 없는 기록과 볼 수 없는 기록을 구분하지 않는다.
  const [record, setRecord] = useState(null)
  const [recordStatus, setRecordStatus] = useState('loading')

  const beginRequest = useLatestRequest()

  // 저장 뒤 다시 읽기도 이 함수를 쓴다. 가장 최근 요청의 응답만 그린다.
  const loadRecord = useCallback(async () => {
    const isLatest = beginRequest()
    try {
      const result = await recordApi.fetchRecord(recordId)
      if (!isLatest()) return
      setRecord(result)
      setRecordStatus('ready')
    } catch (err) {
      if (!isLatest()) return
      setRecordStatus(err instanceof ApiError && err.status === 404 ? 'missing' : 'error')
    }
  }, [recordId, beginRequest])

  useEffect(() => {
    setRecordStatus('loading')
    loadRecord()
  }, [loadRecord])

  const isAuthor = record?.isAuthor ?? false

  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [mapOpen, setMapOpen] = useState(false)
  // 장소·카테고리·평점·메모·사진을 한 번에 고친다. 저장 전까지는 전부 초안이다.
  const [editing, setEditing] = useState(false)
  const [placeDraft, setPlaceDraft] = useState(null)
  const [categoryDraft, setCategoryDraft] = useState('')
  const [ratingDraft, setRatingDraft] = useState(0)
  const [memoDraft, setMemoDraft] = useState('')
  const [photosDraft, setPhotosDraft] = useState([])
  const [editError, setEditError] = useState('')
  const [photoErrors, setPhotoErrors] = useState([])
  const [searchOpen, setSearchOpen] = useState(false)
  // 지울 사진의 위치. 0번도 지울 수 있으므로 없음은 null 로 구분한다.
  const [removingPhotoIndex, setRemovingPhotoIndex] = useState(null)
  // 저장·삭제 요청이 나가 있는 동안 버튼을 막는다.
  const [busy, setBusy] = useState(false)
  // 저장은 됐지만 사진 단계 일부가 실패했을 때의 안내 (공통 명세 §6.2 와 같은 원칙).
  const [saveNotice, setSaveNotice] = useState('')
  const [deleteError, setDeleteError] = useState('')

  // 공개 범위는 소속 여행의 값이고 작성자(= 여행 소유자)에게만 보여준다 (명세 §5.6).
  // 기록 응답에는 없으므로 작성자일 때만 여행을 읽는다. 열람자에게는 내려오지도 않는다.
  const { trip } = useTrip(isAuthor ? record.trip.id : null)

  if (recordStatus === 'loading' || recordStatus === 'error') {
    const loading = recordStatus === 'loading'
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <h1>{loading ? '기록을 불러오는 중이에요…' : '기록을 불러오지 못했습니다'}</h1>
          {!loading && <p className="detail-missing-desc">잠시 후 다시 시도해주세요.</p>}
        </div>
      </main>
    )
  }

  if (recordStatus === 'missing') {
    // 없는 기록과 볼 권한이 없는 기록을 구분해 보여주지 않는다.
    // 문구가 달라지는 순간 그 차이만으로 비공개 기록의 존재가 드러난다.
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <h1>기록을 찾을 수 없습니다</h1>
          <p className="detail-missing-desc">존재하지 않거나 볼 수 없는 기록이에요.</p>
          <Link to="/trips" className="detail-missing-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>
        </div>
      </main>
    )
  }

  const photos = record.photos
  const place = placeOf(record)
  // 소유자에게만 채워지는 값이다. 열람자에게는 null 이다 (backend §4.3.1).
  const sharedGroups = trip?.sharedGroups ?? []
  // 서버는 저장하지 않는 값이다. 기준 위치는 이 브라우저의 것이다 (§5.5).
  const distanceKm = haversineDistanceKm(referenceLocation, place.location)

  const handleMapClick = () => {
    if (isEmbed) {
      setMapOpen(true)
    } else {
      window.open(buildMapsSearchUrl(place), '_blank', 'noopener,noreferrer')
    }
  }

  const handleDelete = async () => {
    setBusy(true)
    try {
      await recordApi.deleteRecord(record.id)
      navigate(`/trips/${record.trip.id}`)
    } catch (err) {
      setDeleteError(err instanceof ApiError ? err.message : '기록을 삭제하지 못했습니다.')
      setBusy(false)
    }
  }

  /** 새로 고른 사진의 미리보기 URL 을 해제한다. 저장·취소 어느 쪽으로 끝나도 부른다. */
  const releasePreviews = (draft) =>
    draft.forEach((p) => p.previewUrl && URL.revokeObjectURL(p.previewUrl))

  const cancelEdit = () => {
    releasePreviews(photosDraft)
    setEditing(false)
  }

  const startEdit = () => {
    setPlaceDraft(place)
    setCategoryDraft(record.category)
    setRatingDraft(record.rating)
    setMemoDraft(record.memo ?? '')
    setPhotosDraft(photos)
    setEditError('')
    setSaveNotice('')
    setPhotoErrors([])
    setEditing(true)
  }

  /** 장소·카테고리·평점·메모·사진을 한 번에 반영한다. 하나라도 검증에 걸리면 아무것도 바꾸지 않는다. */
  const handleSave = async () => {
    if (!placeDraft) {
      setEditError('장소를 검색해 선택해주세요.')
      return
    }
    if (!categoryDraft) {
      setEditError('카테고리를 선택해주세요.')
      return
    }
    if (!ratingDraft) {
      setEditError('평점을 선택해주세요.')
      return
    }
    if (memoDraft.length > MEMO_MAX_LENGTH) {
      setEditError(`메모는 ${MEMO_MAX_LENGTH}자 이하로 입력해주세요.`)
      return
    }

    setBusy(true)
    setEditError('')

    // 1단계: 기록 본문. 전체 갱신이라 입력칸이 없는 태그·도로명주소도 기존 값을 실어 보낸다.
    // 여기서 실패하면 아무것도 바뀌지 않았으므로 수정 폼을 그대로 둔다.
    try {
      await recordApi.updateRecord(record.id, {
        name: placeDraft.name,
        category: categoryDraft,
        tags: record.tags,
        address: placeDraft.address,
        roadAddress: placeDraft.roadAddress ?? null,
        externalLink: placeDraft.link ?? null,
        latitude: placeDraft.location.lat,
        longitude: placeDraft.location.lng,
        rating: ratingDraft,
        memo: memoDraft.trim() || null,
      })
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : '기록을 저장하지 못했습니다.')
      setBusy(false)
      return
    }

    // 2단계: 사진. 본문은 이미 저장됐으므로 여기서 실패해도 되돌리지 않고, 무엇이 안 됐는지만
    // 알린다. 다시 읽은 화면이 실제 남은 사진을 보여준다 (공통 명세 §6.2 와 같은 원칙).
    const failures = []
    const removed = photos.filter((p) => !photosDraft.some((d) => d.id === p.id))
    for (const photo of removed) {
      try {
        await recordApi.deletePhoto(record.id, photo.id)
      } catch {
        failures.push('사진 삭제')
        break
      }
    }
    const added = photosDraft.filter((p) => p.file)
    if (added.length > 0) {
      try {
        await recordApi.uploadPhotos(
          record.id,
          added.map((p) => p.file),
        )
      } catch {
        failures.push('사진 업로드')
      }
    }

    releasePreviews(photosDraft)
    await loadRecord()
    setEditing(false)
    setBusy(false)
    if (failures.length > 0) {
      setSaveNotice(`기록은 저장했지만 ${failures.join('·')}에 실패했습니다. 수정에서 다시 시도해주세요.`)
    }
  }

  const handlePhotoAdd = (e) => {
    const files = Array.from(e.target.files ?? [])
    // 이미 올라간 사진은 다시 보내지 않으므로 합계 검사는 이번에 새로 고른 파일만 대상으로 한다.
    const selectedBytes = photosDraft.reduce((sum, p) => sum + (p.file?.size ?? 0), 0)
    const { accepted, errors: rejected } = validatePhotoFiles(files, selectedBytes)
    setPhotoErrors(rejected)
    if (accepted.length > 0) {
      setPhotosDraft((prev) => [
        ...prev,
        ...accepted.map((file) => ({ file, previewUrl: URL.createObjectURL(file) })),
      ])
    }
    // 같은 파일을 연이어 고를 수 있도록 값을 비운다.
    e.target.value = ''
  }

  /** 초안에서만 뺀다. 이미 올라간 사진은 저장을 눌러야 서버에서 지워진다. */
  const handlePhotoRemove = () => {
    setPhotoErrors([])
    setPhotosDraft((prev) => {
      const target = prev[removingPhotoIndex]
      if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl)
      return prev.filter((_, i) => i !== removingPhotoIndex)
    })
    setRemovingPhotoIndex(null)
  }

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector />
          <HeaderAuth />
        </div>
      </header>

      <main className="detail-page">
        <div className="detail-inner">
          <Link to={`/trips/${record.trip.id}`} className="back-link">
            <ArrowLeftIcon /> {record.trip.name}
          </Link>

          {/* 기록 내용은 카드 하나에 모은다. 소속 여행과 삭제만 따로 둔다. */}
          <section className="detail-summary">
            {editing ? (
              <div className="detail-edit-form">
                <div className="form-field">
                  <label htmlFor="edit-place-name">장소명</label>
                  {/* 장소명은 직접 입력할 수 없다. 검색 결과에서만 채운다 (§5.3). */}
                  <div className="place-search-row">
                    <input
                      id="edit-place-name"
                      type="text"
                      value={placeDraft?.name ?? ''}
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
                  {placeDraft?.address && <p className="field-hint">{placeDraft.address}</p>}
                </div>

                <div className="form-field">
                  <span className="field-label">카테고리</span>
                  <div className="category-choice-group" role="radiogroup" aria-label="카테고리">
                    {SELECTABLE_CATEGORIES.map((c) => (
                      <button
                        key={c.key}
                        type="button"
                        role="radio"
                        aria-checked={categoryDraft === c.key}
                        className={`category-choice${categoryDraft === c.key ? ' active' : ''}`}
                        onClick={() => setCategoryDraft(c.key)}
                      >
                        {categoryIcon(c.key)} {c.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="form-field">
                  <span className="field-label">평점</span>
                  <StarRatingInput value={ratingDraft} onChange={setRatingDraft} />
                </div>

                <div className="form-field">
                  <label htmlFor="edit-memo">메모</label>
                  <textarea
                    id="edit-memo"
                    className="detail-memo-input"
                    rows={4}
                    value={memoDraft}
                    onChange={(e) => setMemoDraft(e.target.value)}
                    placeholder="이 방문에 대한 메모를 남겨보세요"
                  />
                </div>

                <div className="form-field">
                  <span className="field-label">사진</span>
                  <div className="photo-upload-row">
                    <input
                      id="add-photos"
                      className="photo-upload-input"
                      type="file"
                      accept={ALLOWED_PHOTO_ACCEPT}
                      multiple
                      onChange={handlePhotoAdd}
                    />
                    <label htmlFor="add-photos" className="photo-upload-btn">
                      <PhotoIcon />
                      사진 추가
                    </label>
                    <span className="photo-upload-hint">
                      {photosDraft.length > 0
                        ? `${photosDraft.length}장`
                        : `JPG · PNG · WEBP, 한 장당 ${MAX_PHOTO_SIZE_MB}MB · 합계 ${MAX_PHOTO_TOTAL_MB}MB 이하`}
                    </span>
                  </div>
                  {photoErrors.map((message) => (
                    <p className="field-error" key={message}>
                      {message}
                    </p>
                  ))}
                  {photosDraft.length > 0 && (
                    <ul className="detail-photo-grid">
                      {photosDraft.map((photo, i) => (
                        <li key={photo.id ?? photo.previewUrl} className="detail-photo-item">
                          <img
                            src={photoSrc(photo)}
                            alt={`${record.name} 사진 ${i + 1}`}
                            loading="lazy"
                          />
                          <button
                            type="button"
                            className="detail-photo-remove"
                            onClick={() => setRemovingPhotoIndex(i)}
                            aria-label={`사진 ${i + 1} 삭제`}
                          >
                            <CloseIcon />
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                {editError && <p className="field-error">{editError}</p>}

                <div className="detail-edit-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={cancelEdit}
                    disabled={busy}
                  >
                    취소
                  </button>
                  <button type="button" className="btn-primary" onClick={handleSave} disabled={busy}>
                    저장
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="detail-section-head">
                  <div className="detail-summary-head">
                    <span className="detail-category-badge">
                      <span aria-hidden="true">{categoryIcon(record.category)}</span>{' '}
                      {categoryLabel(record.category)}
                    </span>
                    <h1 className="detail-name">{record.name}</h1>
                    <p className="detail-region">{record.address}</p>
                  </div>
                  {isAuthor && (
                    <button type="button" className="btn-secondary" onClick={startEdit}>
                      수정
                    </button>
                  )}
                </div>

                {saveNotice && (
                  <p className="field-error" role="status">
                    {saveNotice}
                  </p>
                )}

                <dl className="detail-meta">
                  <div className="detail-meta-item">
                    <dt>평점</dt>
                    <dd className="detail-meta-accent">★ {record.rating.toFixed(1)}</dd>
                  </div>
                  <div className="detail-meta-item">
                    <dt>거리</dt>
                    <dd>{distanceKm.toFixed(1)}km</dd>
                  </div>
                  <div className="detail-meta-item">
                    <dt>작성자</dt>
                    <dd>{isAuthor ? '나' : record.author.name}</dd>
                  </div>
                  <div className="detail-meta-item">
                    <dt>등록일</dt>
                    <dd>
                      {dateLabel(record.createdAt)}
                      {record.updatedAt !== record.createdAt && ' (수정됨)'}
                    </dd>
                  </div>
                </dl>

                <button type="button" className="detail-map-btn" onClick={handleMapClick}>
                  <MapViewIcon />
                  지도에서 보기
                </button>

                <div className="detail-subsection">
                  <h2 className="detail-section-title">사진</h2>
                  {photos.length === 0 ? (
                    <p className="detail-empty">등록된 사진이 없습니다.</p>
                  ) : (
                    <ul className="detail-photo-grid">
                      {photos.map((photo, i) => (
                        <li key={photo.id} className="detail-photo-item">
                          <img
                            src={fileUrl(photo.url)}
                            alt={`${record.name} 사진 ${i + 1}`}
                            loading="lazy"
                          />
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                {/* 평점과 메모. 기록당 하나뿐이라 평균도 분포도 없다. */}
                <div className="detail-subsection">
                  <h2 className="detail-section-title">평점과 메모</h2>
                  <div className="detail-rating-row">
                    <StarRatingDisplay value={record.rating} />
                    <span className="detail-rating-value">{record.rating.toFixed(1)}</span>
                  </div>
                  {record.memo ? (
                    <p className="detail-memo">{record.memo}</p>
                  ) : (
                    <p className="detail-empty">메모가 없습니다.</p>
                  )}
                </div>
              </>
            )}
          </section>

          {/* 소속 여행. 공개 범위는 여기서 바꾸지 않고 여행 화면에서 바꾼다 (공통 명세 §3.5). */}
          <section className="detail-section">
            <h2 className="detail-section-title">소속 여행</h2>
            <p className="detail-trip-link">
              <Link to={`/trips/${record.trip.id}`}>{record.trip.name}</Link>
            </p>
            {trip && (
              <p className="detail-visibility-current">
                이 여행의 공개 범위 <VisibilityBadge visibility={trip.visibility} />
                {trip.visibility === 'GROUP' && sharedGroups.length > 0 && (
                  <span>· {sharedGroups.map((g) => g.name).join(', ')}</span>
                )}
              </p>
            )}
            <p className="detail-empty">
              공개 범위는 여행 단위로 정해집니다. 바꾸려면 여행 화면에서 변경해주세요.
            </p>
          </section>

          {isAuthor && (
            <section className="detail-section detail-danger-zone">
              <button
                type="button"
                className="detail-delete-btn"
                onClick={() => setConfirmingDelete(true)}
              >
                기록 삭제
              </button>
            </section>
          )}
        </div>
      </main>

      {confirmingDelete && (
        <div
          className="modal-overlay"
          onClick={() => {
            setConfirmingDelete(false)
            setDeleteError('')
          }}
        >
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label="기록 삭제 확인"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>이 기록을 삭제할까요?</h2>
            <p className="confirm-desc">
              삭제하면 목록과 공유 대상 모두에게서 사라집니다. 되돌릴 수 없어요.
            </p>
            {deleteError && <p className="field-error">{deleteError}</p>}
            <div className="confirm-actions">
              <button
                type="button"
                className="confirm-cancel"
                onClick={() => setConfirmingDelete(false)}
              >
                취소
              </button>
              <button type="button" className="confirm-ok" onClick={handleDelete} disabled={busy}>
                삭제
              </button>
            </div>
          </div>
        </div>
      )}

      {removingPhotoIndex !== null && (
        <div className="modal-overlay" onClick={() => setRemovingPhotoIndex(null)}>
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label="사진 삭제 확인"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>이 사진을 삭제할까요?</h2>
            <p className="confirm-desc">
              사진 {removingPhotoIndex + 1}번을 목록에서 뺍니다. 저장을 눌러야 실제로 지워지며,
              취소하면 되돌아옵니다.
            </p>
            <div className="confirm-actions">
              <button
                type="button"
                className="confirm-cancel"
                onClick={() => setRemovingPhotoIndex(null)}
              >
                취소
              </button>
              <button type="button" className="confirm-ok" onClick={handlePhotoRemove}>
                삭제
              </button>
            </div>
          </div>
        </div>
      )}

      {searchOpen && (
        <PlaceSearchModal
          referenceLocation={referenceLocation}
          onSelect={(place) => {
            setPlaceDraft(place)
            setSearchOpen(false)
          }}
          onClose={() => setSearchOpen(false)}
        />
      )}

      {mapOpen && <PlaceMapModal place={place} onClose={() => setMapOpen(false)} />}
    </>
  )
}
