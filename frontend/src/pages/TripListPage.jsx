import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { SCOPES, TRIP_SORT_OPTIONS, tripDurationLabel, tripPeriodLabel, tripVisibility } from '../data/trips.js'
import { fetchTrips } from '../api/trips.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import { useAuth } from '../context/AuthContext.jsx'
import useResource from '../hooks/useResource.js'
import AppHeader from '../components/AppHeader.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import TripCover from '../components/TripCover.jsx'
import { PlusIcon } from '../components/icons.jsx'
import './TripListPage.css'
import { AUTH_STATUS } from '../context/authStatus.js'

/** 페이지 번호를 모두 그리는 최대 쪽수 */
const FULL_PAGINATION_LIMIT = 7

/** 화면에 그릴 페이지 번호. 사이는 '…' 로 줄인다. */
const pageNumbers = (current, total) => {
  if (total <= FULL_PAGINATION_LIMIT) return Array.from({ length: total }, (_, i) => i + 1)
  const shown = [...new Set([1, current - 1, current, current + 1, total])]
    .filter((n) => n >= 1 && n <= total)
    .sort((a, b) => a - b)
  return shown.flatMap((n, i) => (i > 0 && n - shown[i - 1] > 1 ? ['…', n] : [n]))
}

/** 여행 목록 */
export default function TripListPage() {
  const { isLoggedIn, status: authStatus } = useAuth()

  // 범위·정렬·페이지는 주소 쿼리에 둔다
  const [searchParams, setSearchParams] = useSearchParams()
  const scopeParam = searchParams.get('scope')
  const sortParam = searchParams.get('sort')
  const pageParam = searchParams.get('page')

  const visibleScopes = SCOPES.filter((s) => isLoggedIn || !s.requiresLogin)
  const defaultScope = isLoggedIn ? 'MINE' : 'PUBLIC'
  const scope = visibleScopes.some((s) => s.key === scopeParam) ? scopeParam : defaultScope

  const sortKey = TRIP_SORT_OPTIONS.some((o) => o.key === sortParam) ? sortParam : 'RECENT'
  const page = Math.max(1, Number(pageParam) || 1)

  // 화면은 1부터, API 는 0부터 센다
  const apiPage = page - 1
  const loadTrips = useCallback(() => fetchTrips(scope, sortKey, apiPage), [scope, sortKey, apiPage])
  // 로그인 확인 후에 읽는다. updateParams 보다 위에 둬야 React Compiler 최적화가 유지된다.
  const { data: result, status: listStatus, reload } = useResource(loadTrips, authStatus !== AUTH_STATUS.LOADING)

  const updateParams = (next) => {
    const merged = { scope, sort: sortKey, page, ...next }
    const params = {}
    if (merged.scope !== defaultScope) params.scope = merged.scope
    if (merged.sort !== 'RECENT') params.sort = merged.sort
    if (merged.page !== 1) params.page = String(merged.page)
    setSearchParams(params, { replace: true })
  }

  const pageItems = result?.content ?? []
  const totalCount = result?.totalElements ?? 0
  const totalPages = Math.max(1, result?.totalPages ?? 1)

  const emptyMessage = SCOPES.find((s) => s.key === scope)?.empty ?? '표시할 여행이 없어요.'

  /** 여행 카드 목록 */
  const renderTrips = () => {
    if (listStatus === REQUEST_STATUS.ERROR) {
      return (
        <div className="empty-state">
          여행을 불러오지 못했어요.{' '}
          <button type="button" className="link-button" onClick={reload}>
            다시 시도
          </button>
        </div>
      )
    }
    if (listStatus !== REQUEST_STATUS.COMPLETE) {
      return <div className="empty-state">여행을 불러오는 중이에요…</div>
    }
    if (pageItems.length === 0) return <div className="empty-state">{emptyMessage}</div>
    return (
      <ul className="trip-grid">
        {pageItems.map((trip) => {
          const isOwner = trip.isOwner
          return (
            <li key={trip.id} className="trip-card">
              <TripCover trip={trip} />
              <div className="trip-body">
                <h3 className="trip-name">
                  {/* 카드 전체를 덮는 링크 */}
                  <Link to={`/trips/${trip.id}`} className="trip-card-link">
                    {trip.name}
                  </Link>
                </h3>
                <p className="trip-period">
                  {tripPeriodLabel(trip)} ({tripDurationLabel(trip)})
                </p>
                <p className="trip-facts">
                  <span>{trip.headcount}명</span>
                  <span>
                    {trip.recordCount > 0 ? `여행지 ${trip.recordCount}곳` : '아직 등록된 여행지가 없어요'}
                  </span>
                </p>
                <div className="trip-meta">
                  <span className="trip-owner">by {isOwner ? '나' : trip.owner?.name}</span>
                  {isOwner && <VisibilityBadge visibility={tripVisibility(trip)} size="sm" />}
                </div>
                {trip.memo && <p className="trip-memo">{trip.memo}</p>}
              </div>
            </li>
          )
        })}
      </ul>
    )
  }

  return (
    <>
      <AppHeader />

      <main className="app-main">
        <nav className="scope-tabs" aria-label="목록 범위 선택">
          {visibleScopes.map((s) => (
            <button
              key={s.key}
              type="button"
              className={`scope-tab${scope === s.key ? ' active' : ''}`}
              aria-current={scope === s.key ? 'page' : undefined}
              onClick={() => updateParams({ scope: s.key, page: 1 })}
            >
              {s.label}
            </button>
          ))}
        </nav>

        <section className="list-section">
          <div className="list-toolbar">
            <span className="list-count">
              총 <strong>{totalCount}</strong>개의 여행
            </span>
            <div className="list-toolbar-actions">
              <div className="sort-select-wrap">
                <select
                  className="sort-select"
                  value={sortKey}
                  onChange={(e) => updateParams({ sort: e.target.value, page: 1 })}
                  aria-label="정렬 기준"
                >
                  {TRIP_SORT_OPTIONS.map((opt) => (
                    <option key={opt.key} value={opt.key}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
              {isLoggedIn && (
                <Link to="/trips/new" className="btn-primary list-create-btn">
                  <PlusIcon />
                  여행 만들기
                </Link>
              )}
            </div>
          </div>

          {renderTrips()}

          {totalPages > 1 && (
            <div className="pagination" role="navigation" aria-label="페이지 네비게이션">
              <button
                type="button"
                className="page-btn"
                disabled={page === 1}
                onClick={() => updateParams({ page: Math.max(1, page - 1) })}
              >
                이전
              </button>
              {pageNumbers(page, totalPages).map((n, i) =>
                n === '…' ? (
                  <span key={`gap-${i}`} className="page-gap" aria-hidden="true">
                    …
                  </span>
                ) : (
                  <button
                    key={n}
                    type="button"
                    className={`page-btn${n === page ? ' active' : ''}`}
                    aria-current={n === page ? 'page' : undefined}
                    onClick={() => updateParams({ page: n })}
                  >
                    {n}
                  </button>
                ),
              )}
              <button
                type="button"
                className="page-btn"
                disabled={page === totalPages}
                onClick={() => updateParams({ page: Math.min(totalPages, page + 1) })}
              >
                다음
              </button>
            </div>
          )}

          {isLoggedIn && (
            <Link to="/trips/new" className="register-fab">
              <PlusIcon />
              <span className="register-fab-label">여행 만들기</span>
            </Link>
          )}
        </section>
      </main>
    </>
  )
}
