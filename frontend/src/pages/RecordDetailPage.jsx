import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { errorMessage, fileUrl } from '../api/client.js'
import * as recordApi from '../api/records.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import { categoryLabel, placeOf, recordPayload } from '../data/records.js'
import { formatDate } from '../utils/date.js'
import { tripVisibility } from '../data/trips.js'
import useResource from '../hooks/useResource.js'
import useTrip from '../hooks/useTrip.js'
import { haversineDistanceKm } from '../utils/geo.js'
import useMapMode from '../hooks/useMapMode.js'
import usePlaceMap from '../hooks/usePlaceMap.js'
import useReferenceLocation from '../hooks/useReferenceLocation.js'
import AppHeader from '../components/AppHeader.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import DetailStatus from '../components/DetailStatus.jsx'
import StarRatingDisplay from '../components/StarRatingDisplay.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import RecordForm from '../components/RecordForm.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import { ArrowLeftIcon, CategoryIcon, MapViewIcon } from '../components/icons.jsx'
import '../styles/page.css'
import '../styles/detailPage.css'
import './RecordDetailPage.css'

const DELETE_ERROR = {
  RECORD_NOT_FOUND: '이미 삭제된 기록이에요.',
}

const SAVE_ERROR = {
  RECORD_NOT_FOUND: '기록을 찾을 수 없어요. 이미 삭제되었을 수 있어요.',
}

/**
 * 기록 하나의 상세. 작성자(= 소속 여행 소유자)는 같은 카드 안에서 수정·삭제할 수 있다.
 * 공개 범위는 소속 여행의 것을 읽기 전용으로 보여 준다.
 */
export default function RecordDetailPage() {
  const { recordId } = useParams()
  const navigate = useNavigate()
  // 헤더의 설정 메뉴와 같은 상태를 써야 지도 방식을 바꾸는 즉시 이 화면에 반영된다.
  const mapMode = useMapMode()
  const placeMap = usePlaceMap(mapMode.isEmbed)
  // 수정 폼의 장소 검색 모달에서 기준 위치를 바꾸면 이 화면의 거리도 함께 바뀐다.
  const reference = useReferenceLocation()
  const referenceLocation = reference.location

  // 404 는 없는 기록과 볼 수 없는 기록을 구분하지 않는다. 저장 뒤 다시 읽기도 reloadRecord 를 쓴다.
  const loadRecord = useCallback(() => recordApi.fetchRecord(recordId), [recordId])
  const { data: record, status: recordStatus, reload: reloadRecord } = useResource(loadRecord)

  const isAuthor = record?.isAuthor ?? false

  const [confirmingDelete, setConfirmingDelete] = useState(false)
  // 장소·카테고리·평점·메모·사진을 한 번에 고친다. 저장 전까지의 초안은 RecordForm 이 들고 있다.
  const [editing, setEditing] = useState(false)
  const [editError, setEditError] = useState('')
  // 저장·삭제 요청이 나가 있는 동안 버튼을 막는다.
  const [busy, setBusy] = useState(false)
  // 저장은 됐지만 사진 단계 일부가 실패했을 때의 안내. 등록과 같이 본문은 되돌리지 않는다.
  const [saveNotice, setSaveNotice] = useState('')
  const [deleteError, setDeleteError] = useState('')

  // 공개 범위는 소속 여행의 값이고 작성자(= 여행 소유자)에게만 보여준다.
  // 기록 응답에는 없으므로 작성자일 때만 여행을 읽는다. 열람자에게는 내려오지도 않는다.
  const { trip } = useTrip(isAuthor ? record.trip.id : null)

  if (recordStatus === REQUEST_STATUS.ERROR) {
    return <DetailStatus title="기록을 불러오지 못했어요" desc="잠시 후 다시 시도해주세요." />
  }

  if (recordStatus === REQUEST_STATUS.MISSING) {
    // 없는 기록과 볼 권한이 없는 기록을 구분해 보여주지 않는다.
    // 문구가 달라지는 순간 그 차이만으로 비공개 기록의 존재가 드러난다.
    return (
      <DetailStatus
        title="기록을 찾을 수 없어요"
        desc="존재하지 않거나 볼 수 없는 기록이에요."
        backTo="/trips"
        backLabel="여행 목록으로"
      />
    )
  }

  // 상태가 아니라 기록 유무로 가른다. 저장 뒤 다시 읽는 동안 화면이 "불러오는 중" 으로
  // 바뀌지 않게 한다.
  if (!record) return <DetailStatus title="기록을 불러오는 중이에요…" />

  const photos = record.photos
  const place = placeOf(record)
  // 소유자에게만 채워지는 값이다. 열람자에게는 null 이다.
  const sharedGroups = trip?.sharedGroups ?? []
  const sharedWithGroups = trip != null && tripVisibility(trip) === 'GROUP'
  // 서버는 저장하지 않는 값이다. 기준 위치는 이 브라우저의 것이다.
  const distanceKm = haversineDistanceKm(referenceLocation, place.location)

  const closeDeleteConfirm = () => {
    setConfirmingDelete(false)
    setDeleteError('')
  }

  const handleDelete = async () => {
    setBusy(true)
    try {
      await recordApi.deleteRecord(record.id)
      navigate(`/trips/${record.trip.id}`)
    } catch (err) {
      setDeleteError(errorMessage(err, DELETE_ERROR, '기록을 삭제하지 못했어요.'))
      setBusy(false)
    }
  }

  const startEdit = () => {
    setEditError('')
    setSaveNotice('')
    setEditing(true)
  }

  /**
   * 본문을 먼저 저장하고, 사진은 원래 목록과 초안을 비교해 지울 것과 올릴 것을 나눠 반영한다.
   * 검증은 폼이 이미 마쳤다.
   */
  const handleSave = async (values) => {
    setBusy(true)
    setEditError('')

    // 1단계: 기록 본문. 전체 갱신이라 입력칸이 없는 태그도 기존 값을 실어 보낸다.
    // 여기서 실패하면 아무것도 바뀌지 않았으므로 수정 폼을 그대로 둔다.
    try {
      await recordApi.updateRecord(record.id, { ...recordPayload(values), tags: record.tags })
    } catch (err) {
      setEditError(errorMessage(err, SAVE_ERROR, '기록을 저장하지 못했어요.'))
      setBusy(false)
      return
    }

    // 2단계: 사진. 본문은 이미 저장됐으므로 여기서 실패해도 되돌리지 않고, 무엇이 안 됐는지만
    // 알린다. 다시 읽은 화면이 실제 남은 사진을 보여준다.
    const failures = []
    const removed = photos.filter((p) => !values.photos.some((d) => d.id === p.id))
    for (const photo of removed) {
      try {
        await recordApi.deletePhoto(record.id, photo.id)
      } catch {
        failures.push('사진 삭제')
        break
      }
    }
    const added = values.photos.filter((p) => p.file)
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

    await reloadRecord()
    setEditing(false)
    setBusy(false)
    if (failures.length > 0) {
      setSaveNotice(`기록은 저장했지만 ${failures.join('·')}에 실패했어요. 수정에서 다시 시도해주세요.`)
    }
  }

  return (
    <>
      <AppHeader mapMode={mapMode} />

      <main className="page">
        <div className="page-inner">
          <Link to={`/trips/${record.trip.id}`} className="back-link">
            <ArrowLeftIcon /> {record.trip.name}
          </Link>

          {/* 기록 내용은 카드 하나에 모은다. 소속 여행과 삭제만 따로 둔다. */}
          <section className="detail-summary">
            {editing ? (
              <RecordForm
                initialValues={{
                  place,
                  category: record.category,
                  rating: record.rating,
                  memo: record.memo ?? '',
                  photos,
                }}
                reference={reference}
                submitLabel="저장"
                submittingLabel="저장하는 중…"
                submitting={busy}
                error={editError}
                onSubmit={handleSave}
                onCancel={() => setEditing(false)}
              />
            ) : (
              <>
                <div className="detail-section-head">
                  <div className="detail-summary-head">
                    <span className="detail-category-badge">
                      <CategoryIcon category={record.category} />
                      {categoryLabel(record.category)}
                    </span>
                    <h1 className="detail-name">{record.name}</h1>
                    <p className="detail-region">{record.address}</p>
                  </div>
                  {isAuthor && (
                    <div className="detail-author-actions">
                      <button type="button" className="btn-secondary" onClick={startEdit}>
                        수정
                      </button>
                      <button
                        type="button"
                        className="btn-danger-outline"
                        aria-label="기록 삭제"
                        onClick={() => setConfirmingDelete(true)}
                      >
                        삭제
                      </button>
                    </div>
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
                      {formatDate(record.createdAt)}
                      {record.updatedAt !== record.createdAt && ' (수정됨)'}
                    </dd>
                  </div>
                </dl>

                <button
                  type="button"
                  className="btn-secondary detail-map-btn"
                  onClick={() => placeMap.openMap(place)}
                >
                  <MapViewIcon />
                  지도에서 보기
                </button>

                <div className="detail-subsection">
                  <h2 className="detail-section-title">사진</h2>
                  {photos.length === 0 ? (
                    <p className="detail-empty">등록된 사진이 없어요.</p>
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
                  </div>
                  {record.memo ? (
                    <p className="detail-memo">{record.memo}</p>
                  ) : (
                    <p className="detail-empty">메모가 없어요.</p>
                  )}
                </div>
              </>
            )}
          </section>

          {/* 소속 여행. 공개 범위는 여기서 바꾸지 않고 여행 화면에서 바꾼다. */}
          <section className="detail-section">
            <h2 className="detail-section-title">소속 여행</h2>
            <p className="detail-trip-link">
              <Link to={`/trips/${record.trip.id}`}>{record.trip.name}</Link>
            </p>
            {trip && (
              <p className="detail-visibility-current">
                이 여행의 공개 범위 <VisibilityBadge visibility={tripVisibility(trip)} />
                {sharedWithGroups && (
                  <span>· {sharedGroups.map((g) => g.name).join(', ')}</span>
                )}
              </p>
            )}
            <p className="detail-empty">
              공개 범위는 여행 단위로 정해져요. 바꾸려면 여행 화면에서 변경해주세요.
            </p>
          </section>
        </div>
      </main>

      {confirmingDelete && (
        <ConfirmDialog
          title="이 기록을 삭제할까요?"
          error={deleteError}
          confirmLabel="삭제"
          busyLabel="삭제하는 중…"
          busy={busy}
          onConfirm={handleDelete}
          onCancel={closeDeleteConfirm}
        >
          {/* 여행 삭제는 하위 기록을 함께 지우므로, 반대로 기록 하나를 지우면 여행도 사라지는지 헷갈리기 쉽다. */}
          이 기록은 나와 공유받은 사람 모두에게서 사라지지만, &apos;{record.trip.name}&apos; 여행과 다른
          여행지는 그대로 남아요. 되돌릴 수 없어요.
        </ConfirmDialog>
      )}

      {placeMap.mapPlace && <PlaceMapModal place={placeMap.mapPlace} onClose={placeMap.closeMap} />}
    </>
  )
}
