import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { categoryIcon, categoryLabel, visibilityMeta } from '../data/records.js'
import { useRecords } from '../context/RecordsContext.jsx'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import useMapMode from '../hooks/useMapMode.js'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import StarRatingDisplay from '../components/StarRatingDisplay.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import VisibilitySelect from '../components/VisibilitySelect.jsx'
import { ArrowLeftIcon, MapPinIcon, MapViewIcon } from '../components/icons.jsx'
import './RecordDetailPage.css'

export default function RecordDetailPage() {
  const { recordId } = useParams()
  const navigate = useNavigate()
  const { findRecord, currentUser, myGroups, changeVisibility, deleteRecord } = useRecords()
  const { themeKey, changeTheme } = useTheme()
  const { isEmbed } = useMapMode()

  const record = findRecord(recordId)
  const isAuthor = record != null && record.authorId === currentUser.id

  const [visibilityDraft, setVisibilityDraft] = useState(record?.visibility ?? 'PRIVATE')
  const [groupDraft, setGroupDraft] = useState(record?.sharedGroupIds ?? [])
  const [toast, setToast] = useState('')
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [mapOpen, setMapOpen] = useState(false)

  if (!record) {
    // 없는 기록과 볼 권한이 없는 기록을 구분해 보여주지 않는다.
    // 문구가 달라지는 순간 그 차이만으로 비공개 기록의 존재가 드러난다.
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <h1>기록을 찾을 수 없습니다</h1>
          <p className="detail-missing-desc">존재하지 않거나 볼 수 없는 기록이에요.</p>
          <Link to="/records" className="detail-missing-link">
            <ArrowLeftIcon /> 목록으로
          </Link>
        </div>
      </main>
    )
  }

  const photos = record.photos ?? []
  const sharedGroups = myGroups.filter((g) => record.sharedGroupIds.includes(g.id))
  const visibilityChanged =
    visibilityDraft !== record.visibility ||
    groupDraft.join(',') !== record.sharedGroupIds.join(',')

  const handleMapClick = () => {
    if (isEmbed) {
      setMapOpen(true)
    } else {
      window.open(buildMapsSearchUrl(record), '_blank', 'noopener,noreferrer')
    }
  }

  // 범위를 바꾸면 그 결과를 문장으로 알린다. 넓히는 쪽이든 좁히는 쪽이든 누가 볼 수 있게 되는지가 핵심이다.
  const handleVisibilitySave = () => {
    changeVisibility(record.id, visibilityDraft, groupDraft)
    const names = myGroups.filter((g) => groupDraft.includes(g.id)).map((g) => g.name)
    const message =
      visibilityDraft === 'PUBLIC'
        ? '이제 로그인하지 않은 사람도 볼 수 있습니다.'
        : visibilityDraft === 'GROUP' && names.length > 0
          ? `이제 ${names.join(', ')} 멤버가 볼 수 있습니다.`
          : `이제 ${currentUser.name}만 볼 수 있습니다.`
    setToast(message)
    window.setTimeout(() => setToast(''), 4000)
  }

  const handleDelete = () => {
    deleteRecord(record.id)
    navigate('/records')
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
          <Link to="/login" className="header-login-link">
            로그인
          </Link>
        </div>
      </header>

      <main className="detail-page">
        <div className="detail-inner">
          <Link to="/records" className="back-link">
            <ArrowLeftIcon /> 목록으로
          </Link>

          {/* 기본 정보 */}
          <section className="detail-summary">
            <div className="detail-summary-head">
              <span className="detail-category-badge">
                <span aria-hidden="true">{categoryIcon(record.category)}</span>{' '}
                {categoryLabel(record.category)}
              </span>
              <h1 className="detail-name">{record.name}</h1>
              <p className="detail-region">{record.address ?? record.region}</p>
            </div>

            <dl className="detail-meta">
              <div className="detail-meta-item">
                <dt>평점</dt>
                <dd className="detail-meta-accent">★ {record.rating.toFixed(1)}</dd>
              </div>
              <div className="detail-meta-item">
                <dt>거리</dt>
                <dd>{record.distanceKm.toFixed(1)}km</dd>
              </div>
              <div className="detail-meta-item">
                <dt>작성자</dt>
                <dd>{isAuthor ? '나' : record.author.name}</dd>
              </div>
              <div className="detail-meta-item">
                <dt>등록일</dt>
                <dd>
                  {record.createdAt}
                  {record.updatedAt !== record.createdAt && ' (수정됨)'}
                </dd>
              </div>
            </dl>

            <button type="button" className="detail-map-btn" onClick={handleMapClick}>
              <MapViewIcon />
              지도에서 보기
            </button>
          </section>

          {/* 사진 갤러리 */}
          <section className="detail-section">
            <h2 className="detail-section-title">사진</h2>
            {photos.length === 0 ? (
              <p className="detail-empty">등록된 사진이 없습니다.</p>
            ) : (
              <ul className="detail-photo-grid">
                {photos.map((src, i) => (
                  <li key={src} className="detail-photo-item">
                    <img src={src} alt={`${record.name} 사진 ${i + 1}`} loading="lazy" />
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* 평점과 메모. 기록당 하나뿐이라 평균도 분포도 없다. */}
          <section className="detail-section">
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
          </section>

          {/* 공개 범위 — 작성자에게만 보인다. 누구에게 공유했는지는 작성자만 아는 정보다. */}
          {isAuthor && (
            <section className="detail-section detail-visibility-section">
              <h2 className="detail-section-title">공개 범위</h2>
              <p className="detail-visibility-current">
                현재 <VisibilityBadge visibility={record.visibility} />
                {record.visibility === 'GROUP' && sharedGroups.length > 0 && (
                  <span>· {sharedGroups.map((g) => g.name).join(', ')}</span>
                )}
              </p>

              <VisibilitySelect
                value={visibilityDraft}
                onChange={setVisibilityDraft}
                groups={myGroups}
                selectedGroupIds={groupDraft}
                onChangeGroups={setGroupDraft}
                onCreateGroupClick={() => navigate('/groups')}
              />

              <div className="detail-visibility-actions">
                <button
                  type="button"
                  className="btn-primary"
                  disabled={!visibilityChanged}
                  onClick={handleVisibilitySave}
                >
                  공개 범위 저장
                </button>
              </div>
            </section>
          )}

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

      {toast && (
        <p className="detail-toast" role="status">
          {toast}
        </p>
      )}

      {confirmingDelete && (
        <div className="modal-overlay" onClick={() => setConfirmingDelete(false)}>
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
            <div className="confirm-actions">
              <button
                type="button"
                className="confirm-cancel"
                onClick={() => setConfirmingDelete(false)}
              >
                취소
              </button>
              <button type="button" className="confirm-ok" onClick={handleDelete}>
                삭제
              </button>
            </div>
          </div>
        </div>
      )}

      {mapOpen && <PlaceMapModal place={record} onClose={() => setMapOpen(false)} />}
    </>
  )
}
