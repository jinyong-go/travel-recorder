import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CATEGORIES, SCOPES, categoryIcon, categoryLabel } from '../data/records.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useTheme from '../hooks/useTheme.js'
import useMapMode from '../hooks/useMapMode.js'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import SettingsMenu from '../components/SettingsMenu.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import VisibilityBadge from '../components/VisibilityBadge.jsx'
import { MapPinIcon, MapViewIcon, PlusIcon, UsersIcon } from '../components/icons.jsx'

const PAGE_SIZE = 10

const SORT_OPTIONS = [
  { key: 'recent', label: '최근 등록순' },
  // 평균이 아니라 작성자 본인이 매긴 평점이다. 기록마다 평점은 하나뿐이다.
  { key: 'rating', label: '평점순' },
  { key: 'distance', label: '거리순' },
]

const sortRecords = (records, sortKey) => {
  const sorted = [...records]
  switch (sortKey) {
    case 'rating':
      return sorted.sort((a, b) => b.rating - a.rating)
    case 'distance':
      return sorted.sort((a, b) => a.distanceKm - b.distanceKm)
    case 'recent':
    default:
      return sorted.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  }
}

// TODO: 로그인 연동 후 실제 인증 상태로 교체
const IS_LOGGED_IN = true

export default function RecordListPage() {
  const { listByScope, currentUser } = useRecords()
  const { themeKey, changeTheme } = useTheme()
  const { mapMode, changeMapMode, isEmbed } = useMapMode()
  const [mapModalRecord, setMapModalRecord] = useState(null)

  // 범위/필터/정렬/페이지는 URL 쿼리에 둔다. 상세 화면에 다녀와도 목록 상태가 유지되고,
  // 필터링된 목록 자체를 링크로 공유할 수 있다.
  const [searchParams, setSearchParams] = useSearchParams()
  const scopeParam = searchParams.get('scope')
  const categoryParam = searchParams.get('category')
  const sortParam = searchParams.get('sort')
  const pageParam = searchParams.get('page')

  // 비로그인은 "둘러보기" 만 볼 수 있으므로 다른 범위가 들어와도 그쪽으로 떨어뜨린다.
  const visibleScopes = SCOPES.filter((s) => IS_LOGGED_IN || !s.requiresLogin)
  const defaultScope = IS_LOGGED_IN ? 'mine' : 'public'
  const scope = visibleScopes.some((s) => s.key === scopeParam) ? scopeParam : defaultScope

  // 알 수 없는 값이 들어오면 기본값으로 떨어뜨린다.
  const category = CATEGORIES.some((c) => c.key === categoryParam) ? categoryParam : 'all'
  const sortKey = SORT_OPTIONS.some((o) => o.key === sortParam) ? sortParam : 'recent'
  const page = Math.max(1, Number(pageParam) || 1)

  // 기본값인 항목은 URL 에서 빼서 주소를 짧게 유지한다.
  const updateParams = (next) => {
    const merged = { scope, category, sort: sortKey, page, ...next }
    const params = {}
    if (merged.scope !== defaultScope) params.scope = merged.scope
    if (merged.category !== 'all') params.category = merged.category
    if (merged.sort !== 'recent') params.sort = merged.sort
    if (merged.page !== 1) params.page = String(merged.page)
    setSearchParams(params, { replace: true })
  }

  const handleMapButtonClick = (record) => {
    if (isEmbed) {
      setMapModalRecord(record)
    } else {
      window.open(buildMapsSearchUrl(record), '_blank', 'noopener,noreferrer')
    }
  }

  // 메모이제이션은 React Compiler 에 맡긴다. (쿼리 파라미터 기반 값이라 수동 deps 가 유지되지 않음)
  const scoped = listByScope(scope)
  const filtered = sortRecords(
    category === 'all' ? scoped : scoped.filter((r) => r.category === category),
    sortKey,
  )

  const totalCount = filtered.length
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageItems = filtered.slice(
    (currentPage - 1) * PAGE_SIZE,
    (currentPage - 1) * PAGE_SIZE + PAGE_SIZE,
  )

  const emptyMessage = SCOPES.find((s) => s.key === scope)?.empty ?? '표시할 기록이 없습니다.'

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          {IS_LOGGED_IN && (
            <Link to="/groups" className="header-groups-link" aria-label="공유 그룹 관리">
              <UsersIcon />
              <span className="header-groups-label">그룹</span>
            </Link>
          )}
          <SettingsMenu mapMode={mapMode} onChangeMapMode={changeMapMode} />
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
          <Link to="/login" className="header-login-link">
            로그인
          </Link>
        </div>
      </header>

      <main className="app-main">
        {/* 범위 탭. 세 목록은 성격이 달라 한 목록에 섞지 않는다. */}
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

        <nav className="category-tabs" aria-label="카테고리 선택">
          {CATEGORIES.map((c) => (
            <button
              key={c.key}
              type="button"
              className={`category-tab${category === c.key ? ' active' : ''}`}
              onClick={() => updateParams({ category: c.key, page: 1 })}
            >
              {c.label}
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
                {SORT_OPTIONS.map((opt) => (
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
            <ul className="place-grid">
              {pageItems.map((record) => {
                const isAuthor = record.authorId === currentUser.id
                return (
                  <li key={record.id} className="place-card">
                    <div className="place-thumb">
                      <span className="place-thumb-icon">{categoryIcon(record.category)}</span>
                      <span className="place-category-badge">{categoryLabel(record.category)}</span>
                    </div>
                    <div className="place-body">
                      <h3 className="place-name">
                        {/* 카드 전체가 눌리도록 링크를 카드 위에 덮는다 (.place-card-link::after) */}
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
                      {/* 공개 범위는 작성자만 아는 정보다. 남의 기록에는 대신 작성자를 보여준다. */}
                      {isAuthor ? (
                        <VisibilityBadge visibility={record.visibility} size="sm" />
                      ) : (
                        <span className="place-author">by {record.author.name}</span>
                      )}
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

          <Link to="/records/register" className="register-fab">
            <PlusIcon />
            <span className="register-fab-label">등록</span>
          </Link>
        </section>
      </main>

      {mapModalRecord && (
        <PlaceMapModal place={mapModalRecord} onClose={() => setMapModalRecord(null)} />
      )}
    </>
  )
}
