import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { categoryIcon, categoryLabel } from '../data/records.js'
import { useRecords } from '../context/RecordsContext.jsx'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import useMapMode from '../hooks/useMapMode.js'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import StarRatingDisplay from '../components/StarRatingDisplay.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import { ArrowLeftIcon, MapPinIcon, MapViewIcon } from '../components/icons.jsx'
import './RecordDetailPage.css'

export default function RecordDetailPage() {
  const { recordId } = useParams()
  const navigate = useNavigate()
  const { findRecord, tripOf, currentUser, myGroups, deleteRecord } = useRecords()
  const { themeKey, changeTheme } = useTheme()
  const { isEmbed } = useMapMode()

  const record = findRecord(recordId)
  const isAuthor = record != null && record.authorId === currentUser.id

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
          <Link to="/trips" className="detail-missing-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>
        </div>
      </main>
    )
  }

  const photos = record.photos ?? []
  // 공개 범위는 기록이 아니라 소속 여행이 갖는다 (공통 명세 §3.5).
  const trip = tripOf(record)
  const sharedGroups = myGroups.filter((g) => trip.sharedGroupIds.includes(g.id))

  const handleMapClick = () => {
    if (isEmbed) {
      setMapOpen(true)
    } else {
      window.open(buildMapsSearchUrl(record), '_blank', 'noopener,noreferrer')
    }
  }

  const handleDelete = () => {
    deleteRecord(record.id)
    navigate(`/trips/${trip.id}`)
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
          <Link to={`/trips/${trip.id}`} className="back-link">
            <ArrowLeftIcon /> {trip.name}
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

          {/* 소속 여행. 공개 범위는 여기서 바꾸지 않고 여행 화면에서 바꾼다 (공통 명세 §3.5). */}
          <section className="detail-section">
            <h2 className="detail-section-title">소속 여행</h2>
            <p className="detail-trip-link">
              <Link to={`/trips/${trip.id}`}>{trip.name}</Link>
            </p>
            {isAuthor && (
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
