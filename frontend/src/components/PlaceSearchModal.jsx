import { useMemo, useState } from 'react'
import { CloseIcon, SearchIcon } from './icons.jsx'
import { PLACE_SEARCH_RESULTS } from '../data/placeSearchResults.js'
import { haversineDistanceKm } from '../utils/geo.js'
import './PlaceSearchModal.css'

const PAGE_SIZE = 10

/** 검색 결과 거리의 기준이 무엇인지 (명세 §5.5). 확인 중이면 출처보다 그 사실을 먼저 알린다. */
const REFERENCE_LOCATION_LABEL = {
  locating: '현재 위치 확인 중…',
  geo: '현재 위치',
  manual: '직접 지정한 위치',
  default: '기본 위치(서울역)',
}

// 우편번호 검색 방식의 장소 검색 모달. SPECIFICATION.md 5.2 참고.
// 실제 구현에서는 PLACE_SEARCH_RESULTS 목업 대신 백엔드의 네이버 지역 검색
// 프록시 API(거리 계산·페이지네이션까지 처리된 응답)를 호출해야 한다.
export default function PlaceSearchModal({ reference, onSelect, onClose }) {
  const { location: referenceLocation, source, status, setManualLocation } = reference
  // true 면 고른 장소를 기록에 넣지 않고 기준 위치로 저장한다 (명세 §5.4, §5.5).
  const [pickingReference, setPickingReference] = useState(false)
  const title = pickingReference ? '내 위치 지정' : '장소 검색'
  const referenceLabel =
    status === 'locating' ? REFERENCE_LOCATION_LABEL.locating : REFERENCE_LOCATION_LABEL[source]
  const [queryInput, setQueryInput] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState(null)
  const [page, setPage] = useState(1)

  const results = useMemo(() => {
    if (submittedQuery === null) return null
    const q = submittedQuery.trim().toLowerCase()
    const matched = q
      ? PLACE_SEARCH_RESULTS.filter(
          (p) =>
            p.name.toLowerCase().includes(q) ||
            p.region.toLowerCase().includes(q) ||
            p.address.toLowerCase().includes(q),
        )
      : PLACE_SEARCH_RESULTS
    return matched
      .map((p) => ({ ...p, distanceKm: haversineDistanceKm(referenceLocation, p.location) }))
      .sort((a, b) => a.distanceKm - b.distanceKm)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submittedQuery, referenceLocation.lat, referenceLocation.lng])

  const totalCount = results?.length ?? 0
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageItems = results
    ? results.slice((currentPage - 1) * PAGE_SIZE, (currentPage - 1) * PAGE_SIZE + PAGE_SIZE)
    : []

  const handleSearch = (e) => {
    e.preventDefault()
    setSubmittedQuery(queryInput)
    setPage(1)
  }

  const handleSelect = (place) => {
    if (pickingReference) {
      setManualLocation(place.location)
      // 지정을 마치면 장소 검색으로 돌아간다. 새 기준으로 다시 검색하도록 결과를 비운다.
      setPickingReference(false)
      setSubmittedQuery(null)
      setQueryInput('')
      return
    }
    onSelect({
      name: place.name,
      address: place.address,
      region: place.region,
      category: place.category,
      location: place.location,
      // 네이버 지역 검색 결과의 원본 링크. 있을 때만 등록 요청의 externalLink 로 넘어간다.
      link: place.link,
    })
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-panel search-modal-panel"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <button type="button" className="modal-close" onClick={onClose} aria-label="닫기">
          <CloseIcon />
        </button>
        <h2>{title}</h2>

        <form className="place-search-form" onSubmit={handleSearch}>
          <input
            type="text"
            value={queryInput}
            onChange={(e) => setQueryInput(e.target.value)}
            placeholder="장소명 또는 지역을 입력해주세요"
            aria-label="검색어"
            autoFocus
          />
          <button type="submit" className="place-search-submit">
            <SearchIcon />
            검색
          </button>
        </form>

        {!pickingReference && (
          <p className="place-search-reference">
            거리 기준: {referenceLabel}
            <button type="button" className="link-btn" onClick={() => setPickingReference(true)}>
              위치 직접 지정
            </button>
          </p>
        )}

        <div className="place-search-body">
          {submittedQuery === null && (
            <p className="place-search-hint">검색어를 입력하고 검색해주세요.</p>
          )}

          {submittedQuery !== null && totalCount === 0 && (
            <p className="place-search-hint">검색 결과가 없습니다.</p>
          )}

          {totalCount > 0 && (
            <>
              <p className="place-search-count">
                총 <strong>{totalCount}</strong>건 · 가까운 순
              </p>
              <ul className="place-search-list">
                {pageItems.map((p) => (
                  <li key={`${p.name}-${p.address}`}>
                    <button
                      type="button"
                      className="place-search-item"
                      onClick={() => handleSelect(p)}
                    >
                      <span className="place-search-item-name">{p.name}</span>
                      <span className="place-search-item-distance">
                        {p.distanceKm.toFixed(1)}km
                      </span>
                      <span className="place-search-item-address">{p.address}</span>
                    </button>
                  </li>
                ))}
              </ul>

              {totalPages > 1 && (
                <div
                  className="pagination"
                  role="navigation"
                  aria-label="검색 결과 페이지 네비게이션"
                >
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
            </>
          )}
        </div>
      </div>
    </div>
  )
}
