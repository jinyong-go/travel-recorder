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
  TRIP_NOT_FOUND: '이미 삭제된 여행이에요.',
}

const VISIBILITY_ERROR = {
  GROUP_NOT_FOUND: '공유할 수 없는 그룹이 선택되어 있어요. 그룹 선택을 다시 확인해주세요.',
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
  // 헤더의 설정 메뉴와 같은 상태를 써야 지도 방식을 바꾸는 즉시 이 화면에 반영된다.
  const mapMode = useMapMode()
  const placeMap = usePlaceMap(mapMode.isEmbed)
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

  const [confirmingDelete, setConfirmingDelete] = useState(false)
  // 삭제 요청이 나가 있는 동안 같은 버튼을 다시 누르지 못하게 한다.
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  const recordCount = trip.recordCount

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
      setActionError(errorMessage(err, DELETE_ERROR, '여행을 삭제하지 못했어요.'))
      setBusy(false)
    }
  }

  const closeDeleteConfirm = () => {
    setConfirmingDelete(false)
    setActionError('')
  }

  /** 하위 기록 카드 목록. 불러오는 중·실패·빈 목록 안내는 PagedListBody 가 맡는다. */
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
            {/* 대표 사진은 지정된 경우에만 보인다. 없을 때 연·월 자리 표시를 두지 않는 것은
                바로 아래에 기간이 적혀 있어 같은 정보를 두 번 보이게 되기 때문이다. */}
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
            {/* 숫자가 여럿이라 항목명을 붙인다. 간격만으로 나누면 인원·여행지 수·예산이 섞여 읽힌다. */}
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
              {/* 예산은 여행을 볼 수 있는 사람에게 함께 보인다. */}
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

          {/* 범위는 여행에만 있다. 기록 화면에는 바꾸는 수단을 두지 않는다. */}
          {isOwner && <TripVisibilitySection trip={trip} onTripChange={onTripChange} />}

          <section className="detail-section">
            <div className="trip-records-head">
              <h2 className="detail-section-title">여행지 ({recordCount}곳)</h2>
              {/* 0건이면 목록 자리의 큰 버튼이 대신한다. 같은 버튼이 두 개 보이지 않게 한다. */}
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

            {/* 첫 여행지를 남기는 것이 이 화면에서 다음에 할 일이라 목록 자리에서 크게 권한다. */}
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
          {/* 하위 기록이 함께 지워진다는 사실과 건수를 반드시 문구에 담는다. */}
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

/** 저장 결과 문구. 저장된 실제 범위(`effectiveVisibility`)로 고른다. */
const SAVED_SCOPE_TEXT = {
  PUBLIC: '전체 공개돼요',
  GROUP: '선택한 그룹에만 보여요',
  PRIVATE: '이제 나만 볼 수 있어요',
}

/**
 * 여행 상세의 공개 범위 섹션. 소유자에게만 그린다.
 *
 * 초안(범위·공유 그룹)을 고르고 저장한다. 넓히는 변경은 확인 모달을 거치고, 좁히는 변경은 바로
 * 저장한 뒤 결과만 알린다. 범위를 바꾸면 하위 기록이 전부 함께 영향을 받으므로 확인·결과 문구에
 * 여행지 수를 함께 말한다. 저장 응답은 `onTripChange` 로 올려 화면의 여행을 갈아 끼운다.
 */
function TripVisibilitySection({ trip, onTripChange }) {
  const navigate = useNavigate()
  const saved = tripAudience(trip)
  const [visibilityDraft, setVisibilityDraft] = useState(trip.visibility ?? DEFAULT_VISIBILITY)
  const [groupDraft, setGroupDraft] = useState(saved.groupIds)
  // 저장 결과. 성공과 실패를 같은 색으로 보이면 실패가 성공처럼 읽힌다.
  const [message, setMessage] = useState(null)
  const [confirmingWiden, setConfirmingWiden] = useState(false)
  // 요청이 나가 있는 동안 저장을 다시 누르지 못하게 한다.
  const [saving, setSaving] = useState(false)

  const recordCount = trip.recordCount
  const sharedGroups = trip.sharedGroups ?? []
  const changed =
    visibilityDraft !== trip.visibility ||
    (visibilityDraft === 'GROUP' && groupKey(groupDraft) !== groupKey(saved.groupIds))
  const widening = isWideningVisibility(saved, { visibility: visibilityDraft, groupIds: groupDraft })

  /** 범위나 그룹을 다시 고르기 시작하면 지난 저장 결과는 지금 고른 값과 맞지 않으므로 지운다. */
  const changeVisibilityDraft = (next) => {
    setVisibilityDraft(next)
    setMessage(null)
  }
  const changeGroupDraft = (next) => {
    setGroupDraft(next)
    setMessage(null)
  }

  /** 넓히는 변경은 확인 모달을 거친다. 좁히는 변경은 바로 저장하고 결과만 알린다. */
  const handleSaveClick = () => {
    setMessage(null)
    if (widening) setConfirmingWiden(true)
    else handleSave()
  }

  const handleSave = async () => {
    setSaving(true)
    const next = { visibility: visibilityDraft, groupIds: groupDraft }
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

  // 여행을 넓히면 하위 기록과 예산이 전부 함께 보인다. 일부만 공유된다고 오해하지 않게 건수와 함께 말한다.
  const withBudget = trip.budget != null ? '과 예산' : ''
  const widenText =
    visibilityDraft === 'PUBLIC'
      ? `여행지 ${recordCount}곳${withBudget}이 누구에게나 공개돼요. 로그인하지 않은 사람도 둘러보기에서 볼 수 있어요.`
      : `선택한 그룹 ${groupDraft.length}곳의 멤버가 여행지 ${recordCount}곳${withBudget}을 볼 수 있게 돼요.`

  return (
    <>
      {/*
        접기는 details/summary 로 만든다. 직접 만든 토글보다 짧고 키보드·스크린리더
        동작이 기본으로 따라온다. 접혀 있어도 지금 누구에게 보이는지는 요약 줄에 남긴다.
      */}
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
            {/* 영역은 항상 두고 안의 문구만 바꾼다. 그래야 결과가 바뀔 때마다 스크린리더가 읽어 준다. */}
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

      {/* details 밖에 둔다. 섹션을 접어도 모달이 함께 가려지지 않게 한다. */}
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

/**
 * 여행 상세의 하위 기록 카드 한 장. 카드 전체가 기록 상세로 가는 링크이고, 지도 버튼만 따로 눌린다.
 * 공개 범위 배지와 소속 여행 이름은 달지 않는다 — 한 여행의 목록이라 화면 위에 한 번 보인 값과 같다.
 */
function RecordCard({ record, onMapClick }) {
  return (
    <li className="place-card">
      <div className="place-thumb">
        {/* 첫 사진이 있으면 그것을, 없으면 카테고리 아이콘을 둔다. */}
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
