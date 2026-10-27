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

/** 페이지 번호를 모두 그리는 최대 쪽수. 이보다 많으면 가운데를 줄인다. */
const FULL_PAGINATION_LIMIT = 7

/**
 * 그릴 페이지 번호. 첫·끝 페이지와 현재 앞뒤 한 쪽만 남기고 사이를 `'…'` 로 줄인다 — 페이지가 많으면
 * 번호가 여러 줄로 넘쳐 이전·다음 버튼을 찾기 어렵다.
 *
 * @returns 페이지 번호와 `'…'` 의 배열. 예: `[1, '…', 4, 5, 6, '…', 20]`
 */
const pageNumbers = (current, total) => {
  if (total <= FULL_PAGINATION_LIMIT) return Array.from({ length: total }, (_, i) => i + 1)
  const shown = [...new Set([1, current - 1, current, current + 1, total])]
    .filter((n) => n >= 1 && n <= total)
    .sort((a, b) => a - b)
  return shown.flatMap((n, i) => (i > 0 && n - shown[i - 1] > 1 ? ['…', n] : [n]))
}

/**
 * 여행 목록. 내 여행·공유받은 여행·둘러보기 세 범위를 탭으로 나누고, 범위·정렬·페이지는 주소 쿼리에
 * 둔다. 비로그인은 둘러보기만 본다.
 */
export default function TripListPage() {
  const { isLoggedIn, status: authStatus } = useAuth()

  // 범위/정렬/페이지는 URL 쿼리에 둔다. 상세에 다녀와도 목록 상태가 유지되고,
  // 목록 자체를 링크로 공유할 수 있다.
  const [searchParams, setSearchParams] = useSearchParams()
  const scopeParam = searchParams.get('scope')
  const sortParam = searchParams.get('sort')
  const pageParam = searchParams.get('page')

  // 비로그인은 "둘러보기" 만 볼 수 있으므로 다른 범위가 들어와도 그쪽으로 떨어뜨린다.
  const visibleScopes = SCOPES.filter((s) => isLoggedIn || !s.requiresLogin)
  const defaultScope = isLoggedIn ? 'MINE' : 'PUBLIC'
  const scope = visibleScopes.some((s) => s.key === scopeParam) ? scopeParam : defaultScope

  const sortKey = TRIP_SORT_OPTIONS.some((o) => o.key === sortParam) ? sortParam : 'RECENT'
  const page = Math.max(1, Number(pageParam) || 1)

  // 화면의 페이지는 1부터, API 는 0부터 센다. 페이지 크기는 서버가 정한다.
  const apiPage = page - 1
  const loadTrips = useCallback(() => fetchTrips(scope, sortKey, apiPage), [scope, sortKey, apiPage])
  // 로그인 여부를 아직 모르면 기본 탭이 정해지지 않았다. 지금 읽으면 둘러보기를 한 번 읽은 뒤
  // 내 여행으로 다시 읽게 된다. updateParams 보다 위에 둔다 — 아래에 두면 React Compiler 가
  // scope 를 바뀔 수 있는 값으로 보고 이 화면의 최적화를 건너뛴다.
  const { data: result, status: listStatus, reload } = useResource(loadTrips, authStatus !== AUTH_STATUS.LOADING)

  // 기본값인 항목은 URL 에서 빼서 주소를 짧게 유지한다.
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

  /** 목록 자리. 읽는 중·실패·빈 목록이면 안내 한 줄이고, 아니면 여행 카드다. */
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
    // 로그인 확인을 기다리는 동안(IDLE)도 불러오는 중으로 보인다.
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
                  {/* 카드 전체가 눌리도록 링크를 카드 위에 덮는다 (.trip-card-link::after) */}
                  <Link to={`/trips/${trip.id}`} className="trip-card-link">
                    {trip.name}
                  </Link>
                </h3>
                <p className="trip-period">
                  {tripPeriodLabel(trip)} ({tripDurationLabel(trip)})
                </p>
                <p className="trip-facts">
                  <span>{trip.headcount}명</span>
                  {/* 0곳은 숫자 대신 문장으로 쓴다. "여행지 0곳"은 고장 난 값처럼 읽힌다. */}
                  <span>
                    {trip.recordCount > 0 ? `여행지 ${trip.recordCount}곳` : '아직 등록된 여행지가 없어요'}
                  </span>
                </p>
                <div className="trip-meta">
                  <span className="trip-owner">by {isOwner ? '나' : trip.owner?.name}</span>
                  {/* 누구에게 공유했는지는 소유자만 아는 정보다. */}
                  {isOwner && <VisibilityBadge visibility={tripVisibility(trip)} size="sm" />}
                </div>
                {/* 긴 설명은 CSS 로 2줄에서 자른다. 자르는 위치는 글자 폭에 따라 달라져야 한다. */}
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
        {/* 목록을 나누는 탭은 이 한 줄뿐이다. 세 범위는 성격이 달라 한 목록에 섞지 않는다. */}
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
              {/* 넓은 화면에서는 툴바에, 좁은 화면에서는 떠 있는 버튼(.register-fab)으로 둔다.
                  둘 중 하나만 보이도록 CSS 가 가린다. 비로그인에게는 둘 다 그리지 않는다. */}
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
                    // 색만으로 현재 페이지를 알리지 않는다.
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

          {/* 비로그인에게는 할 수 없는 일을 보여 주지 않는다. */}
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
