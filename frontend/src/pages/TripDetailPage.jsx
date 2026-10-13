import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { categoryIcon, categoryLabel, placeOf } from '../data/records.js'
import { DEFAULT_VISIBILITY, isWideningVisibility } from '../data/visibility.js'
import { formatDate } from '../utils/date.js'
import { TRIP_STATUS_MESSAGE, budgetLabel, tripDurationLabel, tripPeriodLabel } from '../data/trips.js'
import { errorMessage, fileUrl } from '../api/client.js'
import { fetchTripRecords } from '../api/records.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import * as tripApi from '../api/trips.js'
import usePagedList from '../hooks/usePagedList.js'
import useReferenceLocation from '../hooks/useReferenceLocation.js'
import useTrip from '../hooks/useTrip.js'
import useMapMode from '../hooks/useMapMode.js'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import AppHeader from '../components/AppHeader.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import DetailStatus from '../components/DetailStatus.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import RecordRegisterModal from '../components/RecordRegisterModal.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import VisibilitySelect from '../components/VisibilitySelect.jsx'
import { ArrowLeftIcon, MapViewIcon, PlusIcon } from '../components/icons.jsx'
// 상세 화면의 공통 레이아웃(.detail-page / .detail-inner / .detail-section)은 기록 상세와 같다.
import '../styles/detailPage.css'
import './TripDetailPage.css'
/** 여행을 서버에서 읽고, 읽는 중·없음·실패를 처리한다. 화면은 여행이 도착한 뒤 TripDetailView 가 그린다. */
export default function TripDetailPage() {
  const { tripId } = useParams()
  const { trip, status, setTrip } = useTrip(tripId)

  if (status === REQUEST_STATUS.COMPLETE) {
    // key 로 여행이 바뀌면 초안 상태를 새로 만든다. 초안의 초깃값이 여행 값이기 때문이다.
    return <TripDetailView key={trip.id} trip={trip} onTripChange={setTrip} />
  }

  return <DetailStatus {...TRIP_STATUS_MESSAGE[status]} backTo="/trips" backLabel="여행 목록으로" />
}

const DELETE_ERROR = {
  TRIP_NOT_FOUND: '이미 삭제된 여행입니다.',
}

const VISIBILITY_ERROR = {
  GROUP_NOT_FOUND: '공유할 수 없는 그룹이 선택되어 있습니다. 그룹 선택을 다시 확인해주세요.',
}

/** 공유 그룹 id 를 순서와 무관하게 비교할 수 있는 문자열로 만든다. */
const groupKey = (ids) => [...ids].sort((a, b) => a - b).join(',')

/**
 * 불러온 여행을 그린다. 수정·공개 범위 변경은 서버 응답으로 `onTripChange` 를 불러 갈아 끼운다.
 *
 * 하위 기록은 등록순으로 한 페이지씩 읽어 "더 보기"로 이어 붙인다. 여행지 수는
 * 목록 길이가 아니라 여행의 `recordCount` 다 — 목록은 아직 다 읽지 않았을 수 있다.
 */
function TripDetailView({ trip, onTripChange }) {
  const navigate = useNavigate()
  const { isEmbed } = useMapMode()
  // 등록 모달과 같은 상태를 써야 모달에서 위치를 지정했을 때 이 화면의 거리도 바뀐다.
  const reference = useReferenceLocation()
  const referenceLocation = reference.location
  const isOwner = trip.isOwner

  // 기준 위치가 바뀌면 거리가 달라지므로 처음부터 다시 읽는다.
  const loadRecords = useCallback(
    (page) => fetchTripRecords(trip.id, referenceLocation, page),
    [trip.id, referenceLocation],
  )
  const records = usePagedList(loadRecords)
  const [registerOpen, setRegisterOpen] = useState(false)
  // 사진만 올리지 못한 채 등록된 기록. 재업로드하러 갈 수 있게 링크를 남긴다.
  const [photoFailedRecord, setPhotoFailedRecord] = useState(null)

  const sharedGroups = trip.sharedGroups ?? []
  const [mapModalRecord, setMapModalRecord] = useState(null)
  const [visibilityDraft, setVisibilityDraft] = useState(trip.visibility ?? DEFAULT_VISIBILITY)
  const [groupDraft, setGroupDraft] = useState(sharedGroups.map((g) => g.id))
  // 공개 범위 저장 결과. 성공과 실패를 같은 색으로 보이면 실패가 성공처럼 읽힌다.
  const [visibilityMessage, setVisibilityMessage] = useState(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [confirmingWiden, setConfirmingWiden] = useState(false)
  // 요청이 나가 있는 동안 같은 버튼을 다시 누르지 못하게 한다.
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  const recordCount = trip.recordCount
  const visibilityChanged =
    visibilityDraft !== trip.visibility ||
    (visibilityDraft === 'GROUP' && groupKey(groupDraft) !== groupKey(sharedGroups.map((g) => g.id)))
  const widening = isWideningVisibility(
    { visibility: trip.visibility, groupIds: sharedGroups.map((g) => g.id) },
    { visibility: visibilityDraft, groupIds: groupDraft },
  )

  const handleMapButtonClick = (record) => {
    const place = placeOf(record)
    if (isEmbed) {
      setMapModalRecord(place)
    } else {
      window.open(buildMapsSearchUrl(place), '_blank', 'noopener,noreferrer')
    }
  }

  /** 등록 후 목록과 여행지 수를 다시 읽는다. 낙관적으로 붙이지 않는다 — 서버가 정한 순서·거리를 따른다. */
  const handleRecordCreated = async (record, { photoFailed }) => {
    setRegisterOpen(false)
    setPhotoFailedRecord(photoFailed ? record : null)
    records.reload()
    try {
      onTripChange(await tripApi.fetchTrip(trip.id))
    } catch {
      // 여행지 수만 늦게 맞춰진다. 다음에 화면에 들어오면 다시 읽는다.
    }
  }

  const handleDelete = async () => {
    setBusy(true)
    try {
      // 하위 기록은 서버가 함께 지운다.
      await tripApi.deleteTrip(trip.id)
      navigate('/trips')
    } catch (err) {
      setActionError(errorMessage(err, DELETE_ERROR, '여행을 삭제하지 못했습니다.'))
      setBusy(false)
    }
  }

  /** 범위나 그룹을 다시 고르기 시작하면 지난 저장 결과는 지금 고른 값과 맞지 않으므로 지운다. */
  const changeVisibilityDraft = (next) => {
    setVisibilityDraft(next)
    setVisibilityMessage(null)
  }
  const changeGroupDraft = (next) => {
    setGroupDraft(next)
    setVisibilityMessage(null)
  }

  /** 넓히는 변경은 확인 모달을 거친다. 좁히는 변경은 바로 저장하고 결과만 알린다. */
  const handleVisibilitySaveClick = () => {
    setVisibilityMessage(null)
    if (widening) setConfirmingWiden(true)
    else handleVisibilitySave()
  }

  /** 범위를 바꾸면 하위 기록이 전부 함께 영향을 받으므로 몇 곳이 영향받는지 함께 알린다. */
  const handleVisibilitySave = async () => {
    setBusy(true)
    try {
      const updated = await tripApi.changeTripVisibility(trip.id, visibilityDraft, groupDraft)
      onTripChange(updated)
      const where =
        updated.visibility === 'PUBLIC'
          ? '전체 공개됩니다'
          : updated.visibility === 'GROUP' && updated.sharedGroups.length > 0
            ? '선택한 그룹에만 보입니다'
            : '이제 나만 볼 수 있습니다'
      setVisibilityMessage({ type: 'success', text: `여행지 ${recordCount}곳이 ${where}.` })
    } catch (err) {
      setVisibilityMessage({
        type: 'error',
        text: errorMessage(err, VISIBILITY_ERROR, '공개 범위를 바꾸지 못했습니다.'),
      })
    } finally {
      setBusy(false)
      setConfirmingWiden(false)
    }
  }

  const closeDeleteConfirm = () => {
    setConfirmingDelete(false)
    setActionError('')
  }

  /** 하위 기록 자리. 실패·첫 로딩·빈 목록이면 안내 한 줄이고, 아니면 카드 목록이다. */
  const renderRecords = () => {
    if (records.status === REQUEST_STATUS.ERROR) {
      return (
        <div className="empty-state">
          여행지를 불러오지 못했습니다.{' '}
          <button type="button" className="link-button" onClick={records.reload}>
            다시 시도
          </button>
        </div>
      )
    }
    if (records.status === REQUEST_STATUS.LOADING && records.items.length === 0) {
      return <div className="empty-state">여행지를 불러오는 중이에요…</div>
    }
    if (records.items.length === 0) {
      return (
        <div className="empty-state">
          아직 등록된 여행지가 없습니다.
          {isOwner && ' 다녀온 곳을 추가해보세요.'}
        </div>
      )
    }
    return (
      <ul className="place-grid">
        {records.items.map((record) => (
          <li key={record.id} className="place-card">
            <div className="place-thumb">
              {/* 첫 사진이 있으면 그것을, 없으면 카테고리 아이콘을 둔다. */}
              {record.thumbnailUrl ? (
                <img src={fileUrl(record.thumbnailUrl)} alt="" className="place-thumb-img" />
              ) : (
                <span className="place-thumb-icon">{categoryIcon(record.category)}</span>
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
                className="place-map-btn"
                onClick={() => handleMapButtonClick(record)}
              >
                <MapViewIcon />
                지도
              </button>
            </div>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <>
      <AppHeader />

      <main className="detail-page">
        <div className="detail-inner">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          <section className="trip-detail-head">
            <div className="trip-detail-title-row">
              <h1 className="detail-name">{trip.name}</h1>
              {isOwner && (
                <div className="detail-author-actions">
                  <Link to={`/trips/${trip.id}/edit`} className="btn-secondary">
                    수정
                  </Link>
                  <button
                    type="button"
                    className="detail-delete-btn"
                    aria-label="여행 삭제"
                    onClick={() => setConfirmingDelete(true)}
                  >
                    삭제
                  </button>
                </div>
              )}
            </div>
            <p className="trip-detail-period">
              {tripPeriodLabel(trip)} ({tripDurationLabel(trip)}) · 인원 {trip.headcount}명 ·
              여행지 {recordCount}곳
            </p>
            <p className="trip-detail-owner">
              by {isOwner ? '나' : trip.owner.name}
              {/* 예산은 여행을 볼 수 있는 사람에게 함께 보인다. */}
              {trip.budget != null && (
                <span className="trip-detail-budget">예산 {budgetLabel(trip.budget)}</span>
              )}
            </p>
            {trip.memo && <p className="trip-detail-memo">{trip.memo}</p>}
          </section>

          {/*
            접기는 details/summary 로 만든다. 직접 만든 토글보다 짧고 키보드·스크린리더
            동작이 기본으로 따라온다. 접혀 있어도 지금 누구에게 보이는지는 요약 줄에 남긴다.
          */}
          {isOwner && (
            <details className="detail-section collapsible-section">
              <summary className="collapsible-summary">
                <span className="detail-section-title">공개 범위</span>
                <span className="detail-visibility-current">
                  <VisibilityBadge visibility={trip.visibility} />
                  {trip.visibility === 'GROUP' && sharedGroups.length > 0 && (
                    <span className="detail-shared-groups">
                      {sharedGroups.map((g) => g.name).join(', ')}
                    </span>
                  )}
                </span>
              </summary>
              <div className="collapsible-body">
                {/* 범위는 여행에만 있다. 기록 화면에는 바꾸는 수단을 두지 않는다. */}
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
                    disabled={!visibilityChanged || busy}
                    onClick={handleVisibilitySaveClick}
                  >
                    {/* 삭제 모달 뒤에 비치는 동안에는 바꾸지 않는다. 그때 나가 있는 요청은 삭제다. */}
                    {busy && !confirmingDelete ? '저장하는 중…' : '저장'}
                  </button>
                  {/* 영역은 항상 두고 안의 문구만 바꾼다. 그래야 결과가 바뀔 때마다 스크린리더가 읽어 준다. */}
                  <span aria-live="polite">
                    {visibilityMessage && (
                      <span
                        className={
                          visibilityMessage.type === 'error' ? 'field-error' : 'detail-saved-msg'
                        }
                      >
                        {visibilityMessage.text}
                      </span>
                    )}
                  </span>
                </div>
              </div>
            </details>
          )}

          <section className="detail-section">
            <div className="trip-records-head">
              <h2 className="detail-section-title">여행지 ({recordCount}곳)</h2>
              {isOwner && (
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
                사진을 올리지 못했습니다.{' '}
                <Link to={`/records/${photoFailedRecord.id}`}>기록 상세</Link>에서 다시 올려주세요.
              </p>
            )}

            {renderRecords()}
            {records.hasNext && (
              <button type="button" className="link-button" onClick={records.loadMore}>
                더 보기
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
          {/* 하위 기록이 함께 지워진다는 사실과 건수를 반드시 문구에 담는다. */}
          이 여행과 여행지 {recordCount}곳의 기록이 함께 삭제됩니다. 되돌릴 수 없어요.
        </ConfirmDialog>
      )}

      {confirmingWiden && (
        <ConfirmDialog
          title="공개 범위를 넓힐까요?"
          confirmLabel="공개 범위 변경"
          busyLabel="변경하는 중…"
          busy={busy}
          safe
          onConfirm={handleVisibilitySave}
          onCancel={() => setConfirmingWiden(false)}
        >
          {/* 여행을 넓히면 하위 기록과 예산이 전부 함께 보인다. 일부만 공유된다고 오해하지 않게 건수와 함께 말한다. */}
          {visibilityDraft === 'PUBLIC'
            ? `여행지 ${recordCount}곳${trip.budget != null ? '과 예산' : ''}이 누구에게나 공개됩니다. 로그인하지 않은 사람도 둘러보기에서 볼 수 있어요.`
            : `선택한 그룹 ${groupDraft.length}곳의 멤버가 여행지 ${recordCount}곳${trip.budget != null ? '과 예산' : ''}을 볼 수 있게 됩니다.`}
        </ConfirmDialog>
      )}

      {mapModalRecord && (
        <PlaceMapModal place={mapModalRecord} onClose={() => setMapModalRecord(null)} />
      )}

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
