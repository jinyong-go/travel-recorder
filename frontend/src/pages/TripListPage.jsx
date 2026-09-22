import { Link, useSearchParams } from 'react-router-dom'
import { SCOPES, categoryIcon } from '../data/records.js'
import { TRIP_SORT_OPTIONS, tripDurationLabel, tripPeriodLabel } from '../data/trips.js'
import { useRecords } from '../context/RecordsContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import useTheme from '../hooks/useTheme.js'
import useMapMode from '../hooks/useMapMode.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import SettingsMenu from '../components/SettingsMenu.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import { MapPinIcon, PlusIcon, UsersIcon } from '../components/icons.jsx'

const PAGE_SIZE = 10

const sortTrips = (trips, sortKey) => {
  const sorted = [...trips]
  switch (sortKey) {
    case 'startDate':
      return sorted.sort((a, b) => new Date(b.startDate) - new Date(a.startDate))
    case 'recent':
    default:
      return sorted.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  }
}

export default function TripListPage() {
  const { listTripsByScope, recordsOfTrip, currentUser, receivedInvites } = useRecords()
  const { isLoggedIn } = useAuth()
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

  // 메모이제이션은 React Compiler 에 맡긴다. (쿼리 파라미터 기반 값이라 수동 deps 가 유지되지 않음)
  const sorted = sortTrips(listTripsByScope(scope), sortKey)
  const totalCount = sorted.length
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageItems = sorted.slice(
    (currentPage - 1) * PAGE_SIZE,
    (currentPage - 1) * PAGE_SIZE + PAGE_SIZE,
  )

  const emptyMessage = SCOPES.find((s) => s.key === scope)?.empty ?? '표시할 여행이 없습니다.'

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          {isLoggedIn && (
            <Link
              to="/groups"
              className="header-groups-link"
              aria-label={
                receivedInvites.length > 0
                  ? `공유 그룹 관리 (받은 초대 ${receivedInvites.length}건)`
                  : '공유 그룹 관리'
              }
            >
              <UsersIcon />
              <span className="header-groups-label">그룹</span>
              {/* 초대함은 그룹 목록을 거쳐 들어간다. 배지로 먼저 알리지 않으면 눈에 띄지 않는다. */}
              {receivedInvites.length > 0 && (
                <span className="header-invite-badge">{receivedInvites.length}</span>
              )}
            </Link>
          )}
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

          {pageItems.length === 0 ? (
            <div className="empty-state">{emptyMessage}</div>
          ) : (
            <ul className="trip-grid">
              {pageItems.map((trip) => {
                const isOwner = trip.ownerId === currentUser.id
                const tripRecords = recordsOfTrip(trip.id)
                return (
                  <li key={trip.id} className="trip-card">
                    <div className="trip-cover">
                      {trip.coverPhotoUrl ? (
                        <img src={trip.coverPhotoUrl} alt="" className="trip-cover-img" />
                      ) : (
                        // 대표 사진이 없으면 첫 여행지의 카테고리 아이콘으로 대신한다.
                        // 무엇을 보여줄지는 화면이 정한다 — 서버는 대체 이미지를 고르지 않는다.
                        <span className="trip-cover-icon">
                          {categoryIcon(tripRecords[0]?.category)}
                        </span>
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
                        {trip.headcount}명 · 여행지 {tripRecords.length}곳
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

          <Link to="/trips/new" className="register-fab">
            <PlusIcon />
            <span className="register-fab-label">여행 만들기</span>
          </Link>
        </section>
      </main>
    </>
  )
}
