import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { categoryIcon, categoryLabel } from '../data/places.js'
import {
  buildRatingDistribution,
  mockRegistrant,
  mockReviewCount,
  mockReviews,
} from '../data/placeDetails.js'
import { usePlaces } from '../context/PlacesContext.jsx'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import useMapMode from '../hooks/useMapMode.js'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import StarRatingInput from '../components/StarRatingInput.jsx'
import StarRatingDisplay from '../components/StarRatingDisplay.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import { ArrowLeftIcon, MapPinIcon, MapViewIcon } from '../components/icons.jsx'
import './PlaceDetailPage.css'

// TODO: 로그인 연동 후 실제 사용자 정보로 교체
const CURRENT_USER = '나'
const IS_LOGGED_IN = true

const today = () => new Date().toISOString().slice(0, 10)

export default function PlaceDetailPage() {
  const { placeId } = useParams()
  const { places } = usePlaces()
  const { themeKey, changeTheme } = useTheme()
  const { isEmbed } = useMapMode()

  const place = places.find((p) => String(p.id) === placeId)

  const [reviews, setReviews] = useState(() => (place ? mockReviews(place) : []))
  const [scoreDraft, setScoreDraft] = useState(0)
  const [contentDraft, setContentDraft] = useState('')
  const [formError, setFormError] = useState('')
  const [mapOpen, setMapOpen] = useState(false)
  const contentRef = useRef(null)

  // 리뷰는 사용자당 1건이므로 본인 리뷰가 있으면 그것이 곧 수정 대상이다.
  const myReview = reviews.find((r) => r.author === CURRENT_USER) ?? null

  if (!place) {
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <h1>여행지를 찾을 수 없습니다</h1>
          <p className="detail-missing-desc">삭제되었거나 잘못된 주소일 수 있어요.</p>
          <Link to="/places" className="detail-missing-link">
            <ArrowLeftIcon /> 목록으로
          </Link>
        </div>
      </main>
    )
  }

  // 본인 리뷰를 반영한 평균/분포. (목업 계산, 서버 연동 시 응답값으로 대체)
  const baseCount = mockReviewCount(place)
  const reviewCount = myReview ? baseCount + 1 : baseCount
  const average = (place.rating * baseCount + (myReview?.score ?? 0)) / Math.max(1, reviewCount)
  const distribution = buildRatingDistribution(average, reviewCount)
  const maxBarCount = Math.max(1, ...distribution.map((d) => d.count))

  const photos = place.photos ?? []

  const handleMapClick = () => {
    if (isEmbed) {
      setMapOpen(true)
    } else {
      window.open(buildMapsSearchUrl(place), '_blank', 'noopener,noreferrer')
    }
  }

  // 별점과 코멘트를 하나의 폼으로 한 번에 제출한다 (SPECIFICATION.md 5.6).
  const handleReviewSubmit = (e) => {
    e.preventDefault()
    if (scoreDraft <= 0) {
      setFormError('별점을 선택해 주세요.')
      return
    }
    setFormError('')
    const content = contentDraft.trim() || null

    setReviews((prev) => {
      const mine = prev.find((r) => r.author === CURRENT_USER)
      if (mine) {
        return prev.map((r) =>
          r.author === CURRENT_USER
            ? { ...r, score: scoreDraft, content, updatedAt: today() }
            : r,
        )
      }
      const createdAt = today()
      return [
        { id: `local-${Date.now()}`, author: CURRENT_USER, score: scoreDraft, content, createdAt, updatedAt: createdAt },
        ...prev,
      ]
    })
  }

  const handleReviewDelete = () => {
    setReviews((prev) => prev.filter((r) => r.author !== CURRENT_USER))
    setScoreDraft(0)
    setContentDraft('')
    setFormError('')
  }

  // 목록의 "수정"은 위쪽 수정 폼으로 데려간다.
  const handleReviewEdit = () => {
    setScoreDraft(myReview.score)
    setContentDraft(myReview.content ?? '')
    contentRef.current?.focus({ preventScroll: false })
    contentRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' })
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
          <Link to="/places" className="back-link">
            <ArrowLeftIcon /> 목록으로
          </Link>

          {/* 기본 정보 */}
          <section className="detail-summary">
            <div className="detail-summary-head">
              <span className="detail-category-badge">
                <span aria-hidden="true">{categoryIcon(place.category)}</span>{' '}
                {categoryLabel(place.category)}
              </span>
              <h1 className="detail-name">{place.name}</h1>
              <p className="detail-region">{place.location?.address ?? place.region}</p>
            </div>

            <dl className="detail-meta">
              <div className="detail-meta-item">
                <dt>평균 별점</dt>
                <dd className="detail-meta-accent">
                  ★ {average.toFixed(1)} <span className="detail-meta-sub">({reviewCount})</span>
                </dd>
              </div>
              <div className="detail-meta-item">
                <dt>거리</dt>
                <dd>{place.distanceKm.toFixed(1)}km</dd>
              </div>
              <div className="detail-meta-item">
                <dt>등록자</dt>
                <dd>{mockRegistrant(place)}</dd>
              </div>
              <div className="detail-meta-item">
                <dt>등록일</dt>
                <dd>{place.registeredAt}</dd>
              </div>
            </dl>

            {place.memo && <p className="detail-memo">{place.memo}</p>}

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
                    <img src={src} alt={`${place.name} 사진 ${i + 1}`} loading="lazy" />
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* 평균 별점 + 분포 */}
          <section className="detail-section">
            <h2 className="detail-section-title">별점</h2>
            <div className="detail-rating-body">
              <div className="detail-rating-score">
                <strong>{average.toFixed(1)}</strong>
                <span className="detail-rating-count">리뷰 {reviewCount}건</span>
              </div>
              <ul className="detail-rating-bars">
                {distribution.map((d) => (
                  <li key={d.score} className="detail-rating-bar-row">
                    <span className="detail-rating-bar-label">{d.score}점</span>
                    <span className="detail-rating-bar-track">
                      <span
                        className="detail-rating-bar-fill"
                        style={{ width: `${(d.count / maxBarCount) * 100}%` }}
                      />
                    </span>
                    <span className="detail-rating-bar-count">{d.count}</span>
                  </li>
                ))}
              </ul>
            </div>
          </section>

          {/* 리뷰 */}
          <section className="detail-section">
            <h2 className="detail-section-title">
              리뷰 <span className="detail-section-count">{reviews.length}</span>
            </h2>

            {IS_LOGGED_IN ? (
              <form className="detail-review-form" onSubmit={handleReviewSubmit}>
                <span className="detail-review-form-title">
                  {myReview ? '내 리뷰 수정' : '리뷰 남기기'}
                </span>
                <div className="detail-review-score-row">
                  <span className="detail-review-score-label">별점</span>
                  <StarRatingInput value={scoreDraft} onChange={setScoreDraft} />
                </div>
                <textarea
                  ref={contentRef}
                  className="detail-review-input"
                  value={contentDraft}
                  onChange={(e) => setContentDraft(e.target.value)}
                  placeholder="코멘트는 선택 사항이에요. 별점만 남겨도 됩니다."
                  rows={3}
                  maxLength={500}
                  aria-label="리뷰 코멘트"
                />
                {formError && (
                  <p className="detail-review-error" role="alert">
                    {formError}
                  </p>
                )}
                <div className="detail-review-form-actions">
                  {myReview && (
                    <button
                      type="button"
                      className="detail-review-delete"
                      onClick={handleReviewDelete}
                    >
                      삭제
                    </button>
                  )}
                  <button type="submit" className="detail-review-submit">
                    {myReview ? '수정' : '등록'}
                  </button>
                </div>
              </form>
            ) : (
              <p className="detail-empty">
                <Link to="/login" className="detail-inline-link">
                  로그인
                </Link>{' '}
                후 리뷰를 남길 수 있어요.
              </p>
            )}

            {reviews.length === 0 ? (
              <p className="detail-empty">아직 리뷰가 없습니다.</p>
            ) : (
              <ul className="detail-review-list">
                {reviews.map((r) => (
                  <li key={r.id} className="detail-review-item">
                    <div className="detail-review-head">
                      <span className="detail-review-author">{r.author}</span>
                      {r.author === CURRENT_USER && (
                        <span className="detail-review-mine-badge">내 리뷰</span>
                      )}
                      <span className="detail-review-date">
                        {r.createdAt}
                        {r.updatedAt !== r.createdAt && ' (수정됨)'}
                      </span>
                      {r.author === CURRENT_USER && (
                        <span className="detail-review-actions">
                          <button type="button" onClick={handleReviewEdit}>
                            수정
                          </button>
                          <button type="button" onClick={handleReviewDelete}>
                            삭제
                          </button>
                        </span>
                      )}
                    </div>
                    <StarRatingDisplay value={r.score} />
                    {r.content && <p className="detail-review-content">{r.content}</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </main>

      {mapOpen && <PlaceMapModal place={place} onClose={() => setMapOpen(false)} />}
    </>
  )
}
