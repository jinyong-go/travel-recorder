import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { categoryIcon, categoryLabel } from '../data/records.js'
import { budgetLabel, tripDurationLabel, tripPeriodLabel } from '../data/trips.js'
import { ApiError } from '../api/client.js'
import * as tripApi from '../api/trips.js'
import { useRecords } from '../context/RecordsContext.jsx'
import { useGroups } from '../context/GroupsContext.jsx'
import useTrip from '../hooks/useTrip.js'
import useTheme from '../hooks/useTheme.js'
import useMapMode from '../hooks/useMapMode.js'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import VisibilitySelect from '../components/VisibilitySelect.jsx'
import TripForm from '../components/TripForm.jsx'
import { ArrowLeftIcon, MapPinIcon, MapViewIcon, PlusIcon } from '../components/icons.jsx'
// 상세 화면의 공통 레이아웃(.detail-page / .detail-inner / .detail-section)은 기록 상세와 같다.
import './RecordDetailPage.css'

/** 여행을 서버에서 읽고, 읽는 중·없음·실패를 처리한다. 화면은 여행이 도착한 뒤 TripDetailView 가 그린다. */
export default function TripDetailPage() {
  const { tripId } = useParams()
  const { trip, status, setTrip } = useTrip(tripId)

  if (status === 'ready') {
    // key 로 여행이 바뀌면 초안 상태를 새로 만든다. 초안의 초깃값이 여행 값이기 때문이다.
    return <TripDetailView key={trip.id} trip={trip} onTripChange={setTrip} />
  }

  // 볼 수 없는 여행과 없는 여행을 구분해 표시하지 않는다. 구분하면 존재가 드러난다.
  const message =
    status === 'loading'
      ? { title: '여행을 불러오는 중이에요…', desc: '' }
      : status === 'error'
        ? { title: '여행을 불러오지 못했습니다', desc: '잠시 후 다시 시도해주세요.' }
        : { title: '여행을 찾을 수 없습니다', desc: '존재하지 않거나 볼 수 없는 여행입니다.' }
  return (
    <main className="detail-page">
      <div className="detail-missing">
        <h1>{message.title}</h1>
        {message.desc && <p className="detail-missing-desc">{message.desc}</p>}
        <Link to="/trips" className="detail-missing-link">
          <ArrowLeftIcon /> 여행 목록으로
        </Link>
      </div>
    </main>
  )
}

/** 공유 그룹 id 를 순서와 무관하게 비교할 수 있는 문자열로 만든다. */
const groupKey = (ids) => [...ids].sort((a, b) => a - b).join(',')

/**
 * 불러온 여행을 그린다. 수정·공개 범위 변경은 서버 응답으로 `onTripChange` 를 불러 갈아 끼운다.
 *
 * 하위 기록은 아직 목업이라 `recordsOfTrip` 에서 온다 (명세 §10.2).
 */
function TripDetailView({ trip, onTripChange }) {
  const navigate = useNavigate()
  const { recordsOfTrip, removeRecordsOfTrip } = useRecords()
  const { groups } = useGroups()
  const { themeKey, changeTheme } = useTheme()
  const { isEmbed } = useMapMode()

  const sharedGroups = trip.sharedGroups ?? []
  const [mapModalRecord, setMapModalRecord] = useState(null)
  const [visibilityDraft, setVisibilityDraft] = useState(trip.visibility ?? 'PRIVATE')
  const [groupDraft, setGroupDraft] = useState(sharedGroups.map((g) => g.id))
  const [savedMessage, setSavedMessage] = useState('')
  const [editOpen, setEditOpen] = useState(false)
  const [editedMessage, setEditedMessage] = useState('')
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  // 확인을 누르기 전까지 수정 값을 들고만 있는다. 반영은 confirmEdit 이 한다.
  const [pendingEdit, setPendingEdit] = useState(null)
  // 요청이 나가 있는 동안 같은 버튼을 다시 누르지 못하게 한다.
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  const isOwner = trip.isOwner
  const tripRecords = recordsOfTrip(trip.id)
  const visibilityChanged =
    visibilityDraft !== trip.visibility ||
    (visibilityDraft === 'GROUP' && groupKey(groupDraft) !== groupKey(sharedGroups.map((g) => g.id)))

  const errorText = (err, fallback) => (err instanceof ApiError ? err.message : fallback)

  const handleMapButtonClick = (record) => {
    if (isEmbed) {
      setMapModalRecord(record)
    } else {
      window.open(buildMapsSearchUrl(record), '_blank', 'noopener,noreferrer')
    }
  }

  /** 폼 제출은 확인 모달을 여는 데서 끝난다. 실제 반영은 확인을 눌러야 일어난다. */
  const handleEditSubmit = (values) => setPendingEdit(values)

  const confirmEdit = async () => {
    setBusy(true)
    try {
      onTripChange(await tripApi.updateTrip(trip.id, pendingEdit))
      setPendingEdit(null)
      setEditOpen(false)
      setEditedMessage('여행 정보를 수정했습니다.')
    } catch (err) {
      // 모달을 닫지 않는다. 입력값이 pendingEdit 에 남아 있어 그대로 다시 시도할 수 있다.
      setActionError(errorText(err, '여행 정보를 수정하지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  /** 서버에서 지운 뒤에만 목업 하위 기록을 걷어낸다. 실패했는데 기록만 사라지면 안 된다. */
  const handleDelete = async () => {
    setBusy(true)
    try {
      await tripApi.deleteTrip(trip.id)
      removeRecordsOfTrip(trip.id)
      navigate('/trips')
    } catch (err) {
      setActionError(errorText(err, '여행을 삭제하지 못했습니다.'))
      setBusy(false)
    }
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
      setSavedMessage(`여행지 ${tripRecords.length}곳이 ${where}.`)
    } catch (err) {
      setSavedMessage(errorText(err, '공개 범위를 바꾸지 못했습니다.'))
    } finally {
      setBusy(false)
    }
  }

  const closeModal = (close) => () => {
    close()
    setActionError('')
  }

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
          <HeaderAuth />
        </div>
      </header>

      <main className="detail-page">
        <div className="detail-inner">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          <section className="trip-detail-head">
            <h1 className="detail-name">{trip.name}</h1>
            <p className="trip-detail-period">
              {tripPeriodLabel(trip)} ({tripDurationLabel(trip)}) · 인원 {trip.headcount}명 ·
              여행지 {tripRecords.length}곳
            </p>
            <p className="trip-detail-owner">
              by {isOwner ? '나' : trip.owner.name}
              {/* 예산은 여행을 볼 수 있는 사람에게 함께 보인다 (공통 명세 §3.5). */}
              {trip.budget != null && (
                <span className="trip-detail-budget">예산 {budgetLabel(trip.budget)}</span>
              )}
            </p>
            {trip.memo && <p className="trip-detail-memo">{trip.memo}</p>}
          </section>

          {isOwner && (
            <>
              {/*
                접기는 details/summary 로 만든다. 직접 만든 토글보다 짧고 키보드·스크린리더
                동작이 기본으로 따라온다. 접혀 있어도 지금 누구에게 보이는지는 요약 줄에 남긴다.
              */}
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
                  {/* 범위는 여행에만 있다. 기록 화면에는 바꾸는 수단을 두지 않는다 (공통 명세 §3.5). */}
                  <VisibilitySelect
                    value={visibilityDraft}
                    onChange={setVisibilityDraft}
                    groups={groups}
                    selectedGroupIds={groupDraft}
                    onChangeGroups={setGroupDraft}
                    onCreateGroupClick={() => navigate('/groups')}
                  />
                  <div className="detail-visibility-actions">
                    <button
                      type="button"
                      className="btn-primary"
                      disabled={!visibilityChanged || busy}
                      onClick={handleVisibilitySave}
                    >
                      저장
                    </button>
                    {savedMessage && <span className="detail-saved-msg">{savedMessage}</span>}
                  </div>
                </div>
              </details>

              <details
                className="detail-section collapsible-section"
                open={editOpen}
                onToggle={(e) => setEditOpen(e.currentTarget.open)}
              >
                <summary className="collapsible-summary">
                  <span className="detail-section-title">여행 정보 수정</span>
                  {editedMessage && <span className="detail-saved-msg">{editedMessage}</span>}
                </summary>
                <div className="collapsible-body">
                  {/* 공개 범위는 이 폼에 없다. 위의 전용 섹션에서만 바꾼다. */}
                  <TripForm
                    initialTrip={trip}
                    submitLabel="수정"
                    onSubmit={handleEditSubmit}
                    onCancel={() => setEditOpen(false)}
                  />
                </div>
              </details>
            </>
          )}

          <section className="detail-section">
            <div className="trip-records-head">
              <h2 className="detail-section-title">여행지 {tripRecords.length}곳</h2>
              {isOwner && (
                <Link to={`/records/register?tripId=${trip.id}`} className="btn-secondary">
                  <PlusIcon />
                  여행지 추가
                </Link>
              )}
            </div>

            {tripRecords.length === 0 ? (
              <div className="empty-state">
                아직 등록된 여행지가 없습니다.
                {isOwner && ' 다녀온 곳을 추가해보세요.'}
              </div>
            ) : (
              <ul className="place-grid">
                {tripRecords.map((record) => (
                  <li key={record.id} className="place-card">
                    <div className="place-thumb">
                      <span className="place-thumb-icon">{categoryIcon(record.category)}</span>
                      <span className="place-category-badge">{categoryLabel(record.category)}</span>
                    </div>
                    <div className="place-body">
                      <h3 className="place-name">
                        <Link to={`/records/${record.id}`} className="place-card-link">
                          {record.name}
                        </Link>
                      </h3>
                      <p className="place-region">{record.region}</p>
                      <div className="place-meta">
                        <span className="place-rating">★ {record.rating.toFixed(1)}</span>
                        <span className="place-distance">{record.distanceKm.toFixed(1)}km</span>
                        <span className="place-date">{record.createdAt}</span>
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
            )}
          </section>
          {isOwner && (
            <section className="detail-section detail-danger-zone">
              <button
                type="button"
                className="detail-delete-btn"
                onClick={() => setConfirmingDelete(true)}
              >
                여행 삭제
              </button>
            </section>
          )}
        </div>
      </main>

      {confirmingDelete && (
        <div className="modal-overlay" onClick={closeModal(() => setConfirmingDelete(false))}>
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label="여행 삭제 확인"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>이 여행을 삭제할까요?</h2>
            {/* 하위 기록이 함께 지워진다는 사실과 건수를 반드시 문구에 담는다 (공통 명세 §3.9). */}
            <p className="confirm-desc">
              이 여행과 여행지 {tripRecords.length}곳의 기록이 함께 삭제됩니다. 되돌릴 수 없어요.
            </p>
            {actionError && <p className="field-error">{actionError}</p>}
            <div className="confirm-actions">
              <button
                type="button"
                className="confirm-cancel"
                onClick={closeModal(() => setConfirmingDelete(false))}
              >
                취소
              </button>
              <button type="button" className="confirm-ok" disabled={busy} onClick={handleDelete}>
                삭제
              </button>
            </div>
          </div>
        </div>
      )}

      {pendingEdit && (
        <div className="modal-overlay" onClick={closeModal(() => setPendingEdit(null))}>
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label="여행 정보 수정 확인"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>여행 정보를 수정할까요?</h2>
            {/* 공개 범위는 이 폼에 없다는 사실을 문구로 확인시킨다 (§9). */}
            <p className="confirm-desc">
              입력한 내용으로 여행 정보가 바뀝니다. 공개 범위는 그대로입니다.
            </p>
            {actionError && <p className="field-error">{actionError}</p>}
            <div className="confirm-actions">
              <button
                type="button"
                className="confirm-cancel"
                onClick={closeModal(() => setPendingEdit(null))}
              >
                취소
              </button>
              <button
                type="button"
                className="confirm-ok confirm-ok-safe"
                disabled={busy}
                onClick={confirmEdit}
              >
                수정
              </button>
            </div>
          </div>
        </div>
      )}

      {mapModalRecord && (
        <PlaceMapModal place={mapModalRecord} onClose={() => setMapModalRecord(null)} />
      )}
    </>
  )
}
