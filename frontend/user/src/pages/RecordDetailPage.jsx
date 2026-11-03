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

/** 수정한 사진을 서버에 반영하고, 실패한 단계 이름 목록을 돌려준다 */
async function syncRecordPhotos(recordId, before, draft) {
  const failures = []
  const removed = before.filter((p) => !draft.some((d) => d.id === p.id))
  for (const photo of removed) {
    try {
      await recordApi.deletePhoto(recordId, photo.id)
    } catch {
      failures.push('사진 삭제')
      break
    }
  }
  const added = draft.filter((p) => p.file)
  if (added.length > 0) {
    try {
      await recordApi.uploadPhotos(
        recordId,
        added.map((p) => p.file),
      )
    } catch {
      failures.push('사진 업로드')
    }
  }
  return failures
}

/** 기록 상세와 수정·삭제 */
export default function RecordDetailPage() {
  const { recordId } = useParams()
  const navigate = useNavigate()
  const mapMode = useMapMode()
  const placeMap = usePlaceMap(mapMode.isEmbed)
  const reference = useReferenceLocation()
  const referenceLocation = reference.location

  const loadRecord = useCallback(() => recordApi.fetchRecord(recordId), [recordId])
  const { data: record, status: recordStatus, reload: reloadRecord } = useResource(loadRecord)

  const isAuthor = record?.isAuthor ?? false

  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editError, setEditError] = useState('')
  const [busy, setBusy] = useState(false)
  // 사진 단계 일부가 실패했을 때의 안내
  const [saveNotice, setSaveNotice] = useState('')
  const [deleteError, setDeleteError] = useState('')

  // 작성자에게만 소속 여행의 공개 범위를 보여 준다
  const { trip } = useTrip(isAuthor ? record.trip.id : null)

  if (recordStatus === REQUEST_STATUS.ERROR) {
    return <DetailStatus title="기록을 불러오지 못했어요" desc="잠시 후 다시 시도해주세요." />
  }

  if (recordStatus === REQUEST_STATUS.MISSING) {
    // 없는 기록과 볼 수 없는 기록은 같은 문구로 보인다
    return (
      <DetailStatus
        title="기록을 찾을 수 없어요"
        desc="존재하지 않거나 볼 수 없는 기록이에요."
        backTo="/trips"
        backLabel="여행 목록으로"
      />
    )
  }

  if (!record) return <DetailStatus title="기록을 불러오는 중이에요…" />

  const photos = record.photos
  const place = placeOf(record)
  const sharedGroups = trip?.sharedGroups ?? []
  const sharedWithGroups = trip != null && tripVisibility(trip) === 'GROUP'
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

  /** 기록 본문과 사진을 저장한다 */
  const handleSave = async (values) => {
    setBusy(true)
    setEditError('')

    // 기록 본문 저장
    try {
      await recordApi.updateRecord(record.id, { ...recordPayload(values), tags: record.tags })
    } catch (err) {
      setEditError(errorMessage(err, SAVE_ERROR, '기록을 저장하지 못했어요.'))
      setBusy(false)
      return
    }

    // 사진 반영
    const failures = await syncRecordPhotos(record.id, photos, values.photos)

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
                <div className="detail-title-row">
                  <div className="detail-summary-head">
                    <span className="detail-category-badge">
                      <CategoryIcon category={record.category} />
                      {categoryLabel(record.category)}
                    </span>
                    <h1 className="detail-name">{record.name}</h1>
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

                <div className="detail-region-row">
                  <p className="detail-region">{record.address}</p>
                  <button
                    type="button"
                    className="btn-secondary btn-sm"
                    onClick={() => placeMap.openMap(place)}
                  >
                    <MapViewIcon />
                    지도에서 보기
                  </button>
                </div>

                <StarRatingDisplay value={record.rating} size={20} />

                {saveNotice && (
                  <p className="field-error" role="status">
                    {saveNotice}
                  </p>
                )}

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

                {record.memo && <p className="detail-memo">{record.memo}</p>}

                <dl className="detail-meta">
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
              </>
            )}
          </section>

          {/* 소속 여행의 공개 범위 안내 */}
          {trip && (
            <section className="detail-section">
              <p className="detail-visibility-current">
                이 여행의 공개 범위 <VisibilityBadge visibility={tripVisibility(trip)} />
                {sharedWithGroups && (
                  <span className="detail-shared-groups">
                    {sharedGroups.map((g) => g.name).join(', ')}
                  </span>
                )}
              </p>
              <p className="detail-visibility-note">
                공개 범위는 여행 단위로 정해져요. 바꾸려면{' '}
                <Link to={`/trips/${record.trip.id}`}>여행 화면</Link>에서 변경해주세요.
              </p>
            </section>
          )}
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
          이 기록은 나와 공유받은 사람 모두에게서 사라지지만, &apos;{record.trip.name}&apos; 여행과 다른
          여행지는 그대로 남아요. 되돌릴 수 없어요.
        </ConfirmDialog>
      )}

      {placeMap.mapPlace && <PlaceMapModal place={placeMap.mapPlace} onClose={placeMap.closeMap} />}
    </>
  )
}
