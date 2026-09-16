import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CATEGORIES, categoryIcon, categoryLabel } from '../data/places.js'
import { usePlaces } from '../context/PlacesContext.jsx'
import useTheme from '../hooks/useTheme.js'
import useMapMode from '../hooks/useMapMode.js'
import { buildMapsSearchUrl } from '../config/mapSettings.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import SettingsMenu from '../components/SettingsMenu.jsx'
import PlaceMapModal from '../components/PlaceMapModal.jsx'
import { MapPinIcon, MapViewIcon, PlusIcon } from '../components/icons.jsx'

const PAGE_SIZE = 10

const SORT_OPTIONS = [
  { key: 'recent', label: '최근 등록순' },
  { key: 'rating', label: '별점순' },
  { key: 'distance', label: '거리순' },
]

const sortPlaces = (places, sortKey) => {
  const sorted = [...places]
  switch (sortKey) {
    case 'rating':
      return sorted.sort((a, b) => b.rating - a.rating)
    case 'distance':
      return sorted.sort((a, b) => a.distanceKm - b.distanceKm)
    case 'recent':
    default:
      return sorted.sort((a, b) => new Date(b.registeredAt) - new Date(a.registeredAt))
  }
}

export default function MainPage() {
  const { places } = usePlaces()
  const { themeKey, changeTheme } = useTheme()
  const { mapMode, changeMapMode, isEmbed } = useMapMode()
  const [mapModalPlace, setMapModalPlace] = useState(null)

  // 필터/정렬/페이지는 URL 쿼리에 둔다. 상세 화면에 다녀와도 목록 상태가 유지되고,
  // 필터링된 목록 자체를 링크로 공유할 수 있다.
  const [searchParams, setSearchParams] = useSearchParams()
  const categoryParam = searchParams.get('category')
  const sortParam = searchParams.get('sort')
  const pageParam = searchParams.get('page')

  // 알 수 없는 값이 들어오면 기본값으로 떨어뜨린다.
  const category = CATEGORIES.some((c) => c.key === categoryParam) ? categoryParam : 'all'
  const sortKey = SORT_OPTIONS.some((o) => o.key === sortParam) ? sortParam : 'recent'
  const page = Math.max(1, Number(pageParam) || 1)

  // 기본값인 항목은 URL 에서 빼서 주소를 짧게 유지한다.
  const updateParams = (next) => {
    const merged = { category, sort: sortKey, page, ...next }
    const params = {}
    if (merged.category !== 'all') params.category = merged.category
    if (merged.sort !== 'recent') params.sort = merged.sort
    if (merged.page !== 1) params.page = String(merged.page)
    setSearchParams(params, { replace: true })
  }

  const handleMapButtonClick = (place) => {
    if (isEmbed) {
      setMapModalPlace(place)
    } else {
      window.open(buildMapsSearchUrl(place), '_blank', 'noopener,noreferrer')
    }
  }

  // 메모이제이션은 React Compiler 에 맡긴다. (쿼리 파라미터 기반 값이라 수동 deps 가 유지되지 않음)
  const filtered = sortPlaces(
    category === 'all' ? places : places.filter((p) => p.category === category),
    sortKey,
  )

  const totalCount = filtered.length
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageItems = filtered.slice(
    (currentPage - 1) * PAGE_SIZE,
    (currentPage - 1) * PAGE_SIZE + PAGE_SIZE,
  )

  const handleCategoryChange = (key) => {
    updateParams({ category: key, page: 1 })
  }

  const handleSortChange = (e) => {
    updateParams({ sort: e.target.value, page: 1 })
  }

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
          <Link to="/login" className="header-login-link">
            로그인
          </Link>
        </div>
      </header>

      <main className="app-main">
        <nav className="category-tabs" aria-label="카테고리 선택">
          {CATEGORIES.map((c) => (
            <button
              key={c.key}
              type="button"
              className={`category-tab${category === c.key ? ' active' : ''}`}
              onClick={() => handleCategoryChange(c.key)}
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
                onChange={handleSortChange}
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
            <div className="empty-state">등록된 여행지가 없습니다.</div>
          ) : (
            <ul className="place-grid">
              {pageItems.map((place) => (
                <li key={place.id} className="place-card">
                  <div className="place-thumb">
                    <span className="place-thumb-icon">{categoryIcon(place.category)}</span>
                    <span className="place-category-badge">{categoryLabel(place.category)}</span>
                  </div>
                  <div className="place-body">
                    <h3 className="place-name">
                      {/* 카드 전체가 눌리도록 링크를 카드 위에 덮는다 (.place-card-link::after) */}
                      <Link to={`/places/${place.id}`} className="place-card-link">
                        {place.name}
                      </Link>
                    </h3>
                    <p className="place-region">{place.region}</p>
                    <div className="place-meta">
                      <span className="place-rating">★ {place.rating.toFixed(1)}</span>
                      <span className="place-distance">{place.distanceKm.toFixed(1)}km</span>
                      <span className="place-date">{place.registeredAt}</span>
                    </div>
                    {place.memo && <p className="place-memo">{place.memo}</p>}
                    <button
                      type="button"
                      className="place-map-btn"
                      onClick={() => handleMapButtonClick(place)}
                    >
                      <MapViewIcon />
                      지도
                    </button>
                  </div>
                </li>
              ))}
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

          <Link to="/places/register" className="register-fab">
            <PlusIcon />
            <span className="register-fab-label">등록</span>
          </Link>
        </section>
      </main>

      {mapModalPlace && (
        <PlaceMapModal place={mapModalPlace} onClose={() => setMapModalPlace(null)} />
      )}

    </>
  )
}
