import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { categoryLabel, placeOf } from '../data/records.js'
import { DEFAULT_VISIBILITY, effectiveVisibility, isWideningVisibility } from '../data/visibility.js'
import { formatDate } from '../utils/date.js'
import { TRIP_STATUS_MESSAGE, budgetLabel, tripAudience, tripDurationLabel, tripPeriodLabel, tripVisibility } from '../data/trips.js'
import { errorMessage, fileUrl } from '../api/client.js'
import { fetchTripRecords } from '../api/records.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import * as tripApi from '../api/trips.js'
import usePagedList from '../hooks/usePagedList.js'
import useReferenceLocation from '../hooks/useReferenceLocation.js'
import useTrip from '../hooks/useTrip.js'
import useMapMode from '../hooks/useMapMode.js'
import usePlaceMap from '../hooks/usePlaceMap.js'
import AppHeader from '../components/AppHeader.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import DetailStatus from '../components/DetailStatus.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import RecordRegisterModal from '../components/RecordRegisterModal.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import VisibilitySelect from '../components/VisibilitySelect.jsx'
import { ArrowLeftIcon, CategoryIcon, ChevronDownIcon, MapViewIcon, PlusIcon } from '../components/icons.jsx'
import '../styles/page.css'
import '../styles/detailPage.css'
import './TripDetailPage.css'
/** 여행 상세 화면 */
export default function TripDetailPage() {
  const { tripId } = useParams()
  const { trip, status, setTrip } = useTrip(tripId)

  if (status === REQUEST_STATUS.COMPLETE) {
    return <TripDetailView key={trip.id} trip={trip} onTripChange={setTrip} />
  }

  return <DetailStatus {...TRIP_STATUS_MESSAGE[status]} backTo="/trips" backLabel="여행 목록으로" />
}

const DELETE_ERROR = {
  TRIP_NOT_FOUND: '이미 삭제된 여행이에요.',
}

const VISIBILITY_ERROR = {
  GROUP_NOT_FOUND: '공유할 수 없는 그룹이 선택되어 있어요. 그룹 선택을 다시 확인해주세요.',
}

/** 공유 그룹 id 비교용 키 */
const groupKey = (ids) => [...ids].sort((a, b) => a - b).join(',')

/** 여행 상세 본문 */
function TripDetailView({ trip, onTripChange }) {
  const navigate = useNavigate()
  const mapMode = useMapMode()
  const placeMap = usePlaceMap(mapMode.isEmbed)
  const reference = useReferenceLocation()
  const referenceLocation = reference.location
  const isOwner = trip.isOwner

  const loadRecords = useCallback(
    (page) => fetchTripRecords(trip.id, referenceLocation, page),
    [trip.id, referenceLocation],
  )
  const records = usePagedList(loadRecords)
  const [registerOpen, setRegisterOpen] = useState(false)
  // 사진 업로드에 실패한 기록
  const [photoFailedRecord, setPhotoFailedRecord] = useState(null)

  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  const recordCount = trip.recordCount

  /** 기록 등록 후 목록과 여행을 다시 읽는다 */
  const handleRecordCreated = async (record, { photoFailed }) => {
    setRegisterOpen(false)
    setPhotoFailedRecord(photoFailed ? record : null)
    records.reload()
    try {
      onTripChange(await tripApi.fetchTrip(trip.id))
    } catch {
      // 실패하면 여행지 수만 늦게 맞춰진다
    }
  }

  const handleDelete = async () => {
    setBusy(true)
    try {
      await tripApi.deleteTrip(trip.id)
      navigate('/trips')
    } catch (err) {
      setActionError(errorMessage(err, DELETE_ERROR, '여행을 삭제하지 못했어요.'))
      setBusy(false)
    }
  }

  const closeDeleteConfirm = () => {
    setConfirmingDelete(false)
    setActionError('')
  }

  /** 기록 카드 목록 */
  const renderRecords = () => (
    <ul className="place-grid">
      {records.items.map((record) => (
        <RecordCard key={record.id} record={record} onMapClick={placeMap.openMap} />
      ))}
    </ul>
  )

  return (
    <>
      <AppHeader mapMode={mapMode} />

      <main className="page">
        <div className="page-inner">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          <section className="trip-detail-head">
            {trip.coverPhotoUrl && (
              <img src={fileUrl(trip.coverPhotoUrl)} alt="" className="trip-detail-cover" />
            )}
            <div className="detail-title-row">
              <h1 className="detail-name">{trip.name}</h1>
              {isOwner && (
                <div className="detail-author-actions">
                  <Link to={`/trips/${trip.id}/edit`} className="btn-secondary">
                    수정
                  </Link>
                  <button
                    type="button"
                    className="btn-danger-outline"
                    aria-label="여행 삭제"
                    onClick={() => setConfirmingDelete(true)}
                  >
                    삭제
                  </button>
                </div>
              )}
            </div>
            <dl className="detail-meta">
              <div className="detail-meta-item detail-meta-wide">
                <dt>기간</dt>
                <dd>
                  {tripPeriodLabel(trip)} ({tripDurationLabel(trip)})
                </dd>
              </div>
              <div className="detail-meta-item">
                <dt>인원</dt>
                <dd>{trip.headcount}명</dd>
              </div>
              <div className="detail-meta-item">
                <dt>여행지</dt>
                <dd>{recordCount}곳</dd>
              </div>
              {trip.budget != null && (
                <div className="detail-meta-item">
                  <dt>예산</dt>
                  <dd>{budgetLabel(trip.budget)}</dd>
                </div>
              )}
              <div className="detail-meta-item">
                <dt>만든 사람</dt>
                <dd>{isOwner ? '나' : trip.owner.name}</dd>
              </div>
            </dl>
            {trip.memo && <p className="trip-detail-memo">{trip.memo}</p>}
          </section>

          {isOwner && <TripVisibilitySection trip={trip} onTripChange={onTripChange} />}

          <section className="detail-section">
            <div className="trip-records-head">
              <h2 className="detail-section-title">여행지 ({recordCount}곳)</h2>
              {isOwner && recordCount > 0 && (
                <button
                  type="button"
                  className="btn-primary trip-records-add-btn"
                  onClick={() => setRegisterOpen(true)}
                >
                  <PlusIcon />
                  여행지 추가
                </button>
              )}
            </div>

            {photoFailedRecord && (
              <p className="field-error" role="status">
                사진을 올리지 못했어요.{' '}
                <Link to={`/records/${photoFailedRecord.id}`}>기록 상세</Link>에서 다시 올려주세요.
              </p>
            )}

            <PagedListBody
              list={records}
              loadingText="여행지를 불러오는 중이에요…"
              errorText="여행지를 불러오지 못했어요."
              emptyText={`아직 등록된 여행지가 없어요.${isOwner ? ' 다녀온 곳을 추가해보세요.' : ''}`}
              noteClassName="empty-state"
              errorClassName="empty-state"
            >
              {renderRecords()}
            </PagedListBody>

            {isOwner && recordCount === 0 && (
              <button
                type="button"
                className="btn-primary trip-records-empty-add"
                onClick={() => setRegisterOpen(true)}
              >
                <PlusIcon />
                여행지 추가
              </button>
            )}
          </section>
        </div>
      </main>

      {confirmingDelete && (
        <ConfirmDialog
          title="이 여행을 삭제할까요?"
          error={actionError}
          confirmLabel="삭제"
          busyLabel="삭제하는 중…"
          busy={busy}
          onConfirm={handleDelete}
          onCancel={closeDeleteConfirm}
        >
          이 여행과 여행지 {recordCount}곳의 기록이 함께 삭제돼요. 되돌릴 수 없어요.
        </ConfirmDialog>
      )}

      {placeMap.mapPlace && <PlaceMapModal place={placeMap.mapPlace} onClose={placeMap.closeMap} />}

      {registerOpen && (
        <RecordRegisterModal
          trip={trip}
          reference={reference}
          onClose={() => setRegisterOpen(false)}
          onCreated={handleRecordCreated}
        />
      )}
    </>
  )
}

/** 저장 결과 문구 */
const SAVED_SCOPE_TEXT = {
  PUBLIC: '전체 공개돼요',
  GROUP: '선택한 그룹에만 보여요',
  PRIVATE: '이제 나만 볼 수 있어요',
}

/** 여행의 공개 범위 설정 섹션 */
function TripVisibilitySection({ trip, onTripChange }) {
  const navigate = useNavigate()
  const saved = tripAudience(trip)
  const [visibilityDraft, setVisibilityDraft] = useState(trip.visibility ?? DEFAULT_VISIBILITY)
  const [groupDraft, setGroupDraft] = useState(saved.groupIds)
  const [message, setMessage] = useState(null)
  const [confirmingWiden, setConfirmingWiden] = useState(false)
  const [saving, setSaving] = useState(false)

  const recordCount = trip.recordCount
  const sharedGroups = trip.sharedGroups ?? []
  const changed =
    visibilityDraft !== trip.visibility ||
    (visibilityDraft === 'GROUP' && groupKey(groupDraft) !== groupKey(saved.groupIds))
  const widening = isWideningVisibility(saved, { visibility: visibilityDraft, groupIds: groupDraft })

  /** 공개 범위 초안을 바꾼다 */
  const changeVisibilityDraft = (next) => {
    setVisibilityDraft(next)
    setMessage(null)
  }
  const changeGroupDraft = (next) => {
    setGroupDraft(next)
    setMessage(null)
  }

  /** 공개 범위를 저장한다. 넓히는 변경은 확인을 거친다. */
  const handleSaveClick = () => {
    setMessage(null)
    if (widening) setConfirmingWiden(true)
    else handleSave()
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const updated = await tripApi.changeTripVisibility(trip.id, visibilityDraft, groupDraft)
      onTripChange(updated)
      const where = SAVED_SCOPE_TEXT[effectiveVisibility(tripAudience(updated))]
      setMessage({ type: 'success', text: `여행지 ${recordCount}곳이 ${where}.` })
    } catch (err) {
      setMessage({ type: 'error', text: errorMessage(err, VISIBILITY_ERROR, '공개 범위를 바꾸지 못했어요.') })
    } finally {
      setSaving(false)
      setConfirmingWiden(false)
    }
  }

  const withBudget = trip.budget != null ? '과 예산' : ''
  const widenText =
    visibilityDraft === 'PUBLIC'
      ? `여행지 ${recordCount}곳${withBudget}이 누구에게나 공개돼요. 로그인하지 않은 사람도 둘러보기에서 볼 수 있어요.`
      : `선택한 그룹 ${groupDraft.length}곳의 멤버가 여행지 ${recordCount}곳${withBudget}을 볼 수 있게 돼요.`

  return (
    <>
      <details className="detail-section collapsible-section">
        <summary className="collapsible-summary">
          <ChevronDownIcon className="collapsible-chevron" aria-hidden="true" />
          <span className="detail-section-title">공개 범위</span>
          <span className="detail-visibility-current">
            <VisibilityBadge visibility={tripVisibility(trip)} />
            {effectiveVisibility(saved) === 'GROUP' && (
              <span className="detail-shared-groups">
                {sharedGroups.map((g) => g.name).join(', ')}
              </span>
            )}
          </span>
        </summary>
        <div className="collapsible-body">
          <VisibilitySelect
            value={visibilityDraft}
            onChange={changeVisibilityDraft}
            selectedGroupIds={groupDraft}
            onChangeGroups={changeGroupDraft}
            onCreateGroupClick={() => navigate('/groups')}
          />
          <div className="detail-visibility-actions">
            <button
              type="button"
              className="btn-primary"
              disabled={!changed || saving}
              onClick={handleSaveClick}
            >
              {saving ? '저장하는 중…' : '저장'}
            </button>
            <span aria-live="polite">
              {message && (
                <span className={message.type === 'error' ? 'field-error' : 'detail-saved-msg'}>
                  {message.text}
                </span>
              )}
            </span>
          </div>
        </div>
      </details>

      {confirmingWiden && (
        <ConfirmDialog
          title="공개 범위를 넓힐까요?"
          confirmLabel="공개 범위 변경"
          busyLabel="변경하는 중…"
          busy={saving}
          safe
          onConfirm={handleSave}
          onCancel={() => setConfirmingWiden(false)}
        >
          {widenText}
        </ConfirmDialog>
      )}
    </>
  )
}

/** 기록 카드 */
function RecordCard({ record, onMapClick }) {
  return (
    <li className="place-card">
      <div className="place-thumb">
        {record.thumbnailUrl ? (
          <img src={fileUrl(record.thumbnailUrl)} alt="" className="place-thumb-img" />
        ) : (
          <CategoryIcon category={record.category} className="place-thumb-icon" />
        )}
        <span className="place-category-badge">{categoryLabel(record.category)}</span>
      </div>
      <div className="place-body">
        <h3 className="place-name">
          <Link to={`/records/${record.id}`} className="place-card-link">
            {record.name}
          </Link>
        </h3>
        <p className="place-region">{record.address}</p>
        <div className="place-meta">
          <span className="place-rating">★ {record.rating.toFixed(1)}</span>
          {record.distanceKm != null && (
            <span className="place-distance">{record.distanceKm.toFixed(1)}km</span>
          )}
          <span className="place-date">{formatDate(record.createdAt)}</span>
        </div>
        {record.memo && <p className="place-memo">{record.memo}</p>}
        <button
          type="button"
          className="btn-secondary btn-sm place-map-btn"
          onClick={() => onMapClick(placeOf(record))}
        >
          <MapViewIcon />
          지도
        </button>
      </div>
    </li>
  )
}
