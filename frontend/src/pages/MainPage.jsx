import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CATEGORIES, categoryIcon, categoryLabel } from '../data/places.js'
import { usePlaces } from '../context/PlacesContext.jsx'
import { applyTheme, getInitialTheme, storeTheme } from '../theme/themes.js'
import {
  buildMapsEmbedUrl,
  buildMapsSearchUrl,
  getStoredMapMode,
  hasGoogleMapsApiKey,
  storeMapMode,
} from '../config/mapSettings.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import SettingsMenu from '../components/SettingsMenu.jsx'
import { CloseIcon, MapPinIcon, MapViewIcon, PlusIcon } from '../components/icons.jsx'

const PAGE_SIZE = 10

const SORT_OPTIONS = [
  { key: 'recent', label: '최근 등록순' },
  { key: 'rating', label: '평점순' },
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
  const [category, setCategory] = useState('all')
  const [sortKey, setSortKey] = useState('recent')
  const [page, setPage] = useState(1)
  const [themeKey, setThemeKey] = useState(getInitialTheme)
  const [mapMode, setMapMode] = useState(() => {
    const stored = getStoredMapMode()
    return stored === 'embed' && hasGoogleMapsApiKey ? 'embed' : 'link'
  })
  const [mapModalPlace, setMapModalPlace] = useState(null)

  useEffect(() => {
    applyTheme(themeKey)
  }, [themeKey])

  const handleThemeChange = (key) => {
    setThemeKey(key)
    storeTheme(key)
  }

  const handleMapModeChange = (key) => {
    setMapMode(key)
    storeMapMode(key)
  }

  const handleMapButtonClick = (place) => {
    if (mapMode === 'embed' && hasGoogleMapsApiKey) {
      setMapModalPlace(place)
    } else {
      window.open(buildMapsSearchUrl(place), '_blank', 'noopener,noreferrer')
    }
  }

  const filtered = useMemo(() => {
    const byCategory = category === 'all' ? places : places.filter((p) => p.category === category)
    return sortPlaces(byCategory, sortKey)
  }, [places, category, sortKey])

  const totalCount = filtered.length
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageItems = filtered.slice(
    (currentPage - 1) * PAGE_SIZE,
    (currentPage - 1) * PAGE_SIZE + PAGE_SIZE,
  )

  const handleCategoryChange = (key) => {
    setCategory(key)
    setPage(1)
  }

  const handleSortChange = (e) => {
    setSortKey(e.target.value)
    setPage(1)
  }

  return (
    <>
      <header className="app-header">
        <h1 className="app-title">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </h1>
        <div className="header-actions">
          <SettingsMenu mapMode={mapMode} onChangeMapMode={handleMapModeChange} />
          <ThemeSelector themeKey={themeKey} onChange={handleThemeChange} />
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
                    <h3 className="place-name">{place.name}</h3>
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
                onClick={() => setPage((p) => Math.max(1, p - 1))}
              >
                이전
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map((n) => (
                <button
                  key={n}
                  type="button"
                  className={`page-btn${n === currentPage ? ' active' : ''}`}
                  onClick={() => setPage(n)}
                >
                  {n}
                </button>
              ))}
              <button
                type="button"
                className="page-btn"
                disabled={currentPage === totalPages}
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
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
        <div className="modal-overlay" onClick={() => setMapModalPlace(null)}>
          <div
            className="modal-panel map-modal-panel"
            role="dialog"
            aria-modal="true"
            aria-label={`${mapModalPlace.name} 지도`}
            onClick={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              className="modal-close"
              onClick={() => setMapModalPlace(null)}
              aria-label="닫기"
            >
              <CloseIcon />
            </button>
            <h2>{mapModalPlace.name}</h2>
            <p className="modal-desc">{mapModalPlace.region}</p>
            <iframe
              className="map-embed-frame"
              title={`${mapModalPlace.name} 지도`}
              src={buildMapsEmbedUrl(mapModalPlace)}
              loading="lazy"
              referrerPolicy="no-referrer-when-downgrade"
              allowFullScreen
            />
            <a
              className="map-embed-link"
              href={buildMapsSearchUrl(mapModalPlace)}
              target="_blank"
              rel="noopener noreferrer"
            >
              새 창에서 구글 지도로 열기
            </a>
          </div>
        </div>
      )}
    </>
  )
}
