import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { SCOPES, categoryIcon } from '../data/records.js'
import { TRIP_SORT_OPTIONS, tripDurationLabel, tripPeriodLabel } from '../data/trips.js'
import { fetchTrips } from '../api/trips.js'
import { useAuth } from '../context/AuthContext.jsx'
import useTheme from '../hooks/useTheme.js'
import useMapMode from '../hooks/useMapMode.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import SettingsMenu from '../components/SettingsMenu.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import { MapPinIcon, PlusIcon } from '../components/icons.jsx'

export default function TripListPage() {
  const { isLoggedIn, status: authStatus } = useAuth()
  const { themeKey, changeTheme } = useTheme()
  const { mapMode, changeMapMode } = useMapMode()

  // 범위/정렬/페이지는 URL 쿼리에 둔다. 상세에 다녀와도 목록 상태가 유지되고,
  // 목록 자체를 링크로 공유할 수 있다.
  const [searchParams, setSearchParams] = useSearchParams()
  const scopeParam = searchParams.get('scope')
  const sortParam = searchParams.get('sort')
  const pageParam = searchParams.get('page')

  // 비로그인은 "둘러보기" 만 볼 수 있으므로 다른 범위가 들어와도 그쪽으로 떨어뜨린다.
  const visibleScopes = SCOPES.filter((s) => isLoggedIn || !s.requiresLogin)
  const defaultScope = isLoggedIn ? 'mine' : 'public'
  const scope = visibleScopes.some((s) => s.key === scopeParam) ? scopeParam : defaultScope

  const sortKey = TRIP_SORT_OPTIONS.some((o) => o.key === sortParam) ? sortParam : 'recent'
  const page = Math.max(1, Number(pageParam) || 1)

  // 기본값인 항목은 URL 에서 빼서 주소를 짧게 유지한다.
  const updateParams = (next) => {
    const merged = { scope, sort: sortKey, page, ...next }
    const params = {}
    if (merged.scope !== defaultScope) params.scope = merged.scope
    if (merged.sort !== 'recent') params.sort = merged.sort
    if (merged.page !== 1) params.page = String(merged.page)
    setSearchParams(params, { replace: true })
  }

  // 'loading' | 'ready' | 'error'. 다시 시도 버튼이 같은 조건으로 한 번 더 읽도록 reloadKey 를 둔다.
  const [result, setResult] = useState(null)
  const [listStatus, setListStatus] = useState('loading')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    // 로그인 여부를 아직 모르면 기본 탭이 정해지지 않았다. 지금 읽으면 둘러보기를 한 번 읽은 뒤
    // 내 여행으로 다시 읽게 된다.
    if (authStatus === 'loading') return

    // 탭·정렬·페이지를 빠르게 바꿀 때 늦게 도착한 이전 응답이 화면을 덮지 않게 한다.
    let cancelled = false
    setListStatus('loading')
    // 화면의 페이지는 1부터, API 는 0부터 센다. 페이지 크기는 서버가 정한다 (backend §4.1).
    fetchTrips(scope, sortKey, page - 1)
      .then((next) => {
        if (cancelled) return
        setResult(next)
        setListStatus('ready')
      })
      .catch(() => {
        if (!cancelled) setListStatus('error')
      })

    return () => {
      cancelled = true
    }
  }, [authStatus, scope, sortKey, page, reloadKey])

  const pageItems = result?.content ?? []
  const totalCount = result?.totalElements ?? 0
  const totalPages = Math.max(1, result?.totalPages ?? 1)
  const currentPage = page

  const emptyMessage = SCOPES.find((s) => s.key === scope)?.empty ?? '표시할 여행이 없습니다.'

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <SettingsMenu mapMode={mapMode} onChangeMapMode={changeMapMode} />
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
          <HeaderAuth />
        </div>
      </header>

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
              총 <strong>{totalCount}</strong>건
            </span>
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
          </div>

          {listStatus === 'loading' ? (
            <div className="empty-state">여행을 불러오는 중이에요…</div>
          ) : listStatus === 'error' ? (
            <div className="empty-state">
              여행을 불러오지 못했습니다.{' '}
              <button type="button" className="link-button" onClick={() => setReloadKey((k) => k + 1)}>
                다시 시도
              </button>
            </div>
          ) : pageItems.length === 0 ? (
            <div className="empty-state">{emptyMessage}</div>
          ) : (
            <ul className="trip-grid">
              {pageItems.map((trip) => {
                const isOwner = trip.isOwner
                return (
                  <li key={trip.id} className="trip-card">
                    <div className="trip-cover">
                      {trip.coverPhotoUrl ? (
                        <img src={trip.coverPhotoUrl} alt="" className="trip-cover-img" />
                      ) : (
                        // 대표 사진이 없으면 플레이스홀더를 둔다. 무엇을 보여줄지는 화면이
                        // 정한다 — 서버는 대체 이미지를 고르지 않는다 (§4.4).
                        <span className="trip-cover-icon">{categoryIcon()}</span>
                      )}
                    </div>
                    <div className="trip-body">
                      <h3 className="trip-name">
                        {/* 카드 전체가 눌리도록 링크를 카드 위에 덮는다 (.trip-card-link::after) */}
                        <Link to={`/trips/${trip.id}`} className="trip-card-link">
                          {trip.name}
                        </Link>
                      </h3>
                      <p className="trip-period">
                        {tripPeriodLabel(trip)} ({tripDurationLabel(trip)}) · 인원{' '}
                        {trip.headcount}명 · 여행지 {trip.recordCount}곳
                      </p>
                      <div className="trip-meta">
                        <span className="trip-owner">by {isOwner ? '나' : trip.owner?.name}</span>
                        {/* 누구에게 공유했는지는 소유자만 아는 정보다 (공통 명세 §3.5). */}
                        {isOwner && <VisibilityBadge visibility={trip.visibility} size="sm" />}
                      </div>
                      {/* 긴 설명은 CSS 로 2줄에서 자른다. 자르는 위치는 글자 폭에 따라 달라져야 한다. */}
                      {trip.memo && <p className="trip-memo">{trip.memo}</p>}
                    </div>
                  </li>
                )
              })}
            </ul>
          )}

          {totalPages > 1 && (
            <div className="pagination" role="navigation" aria-label="페이지 네비게이션">
              <button
                type="button"
                className="page-btn"
                disabled={currentPage === 1}
                onClick={() => updateParams({ page: Math.max(1, currentPage - 1) })}
              >
                이전
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map((n) => (
                <button
                  key={n}
                  type="button"
                  className={`page-btn${n === currentPage ? ' active' : ''}`}
                  onClick={() => updateParams({ page: n })}
                >
                  {n}
                </button>
              ))}
              <button
                type="button"
                className="page-btn"
                disabled={currentPage === totalPages}
                onClick={() => updateParams({ page: Math.min(totalPages, currentPage + 1) })}
              >
                다음
              </button>
            </div>
          )}

          {/* 비로그인에게는 할 수 없는 일을 보여 주지 않는다 (명세 §2.2). */}
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
