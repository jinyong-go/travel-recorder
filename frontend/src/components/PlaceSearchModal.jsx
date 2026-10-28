import { useCallback, useMemo, useState } from 'react'
import useModal from '../hooks/useModal.js'
import useResource from '../hooks/useResource.js'
import { searchPlaces } from '../api/places.js'
import { errorMessage } from '../api/client.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import { CloseIcon, SearchIcon } from './icons.jsx'
import { haversineDistanceKm } from '../utils/geo.js'
import { PLACE_SEARCH_RESULT_LIMIT } from '../config/placeSearch.js'
import './PlaceSearchModal.css'

const SEARCH_ERROR = {
  PLACE_SEARCH_UNAVAILABLE: '지금은 장소 검색을 할 수 없어요. 잠시 후 다시 시도해주세요.',
}

/** 기준 위치 출처 문구 */
const REFERENCE_LOCATION_LABEL = {
  locating: '현재 위치 확인 중…',
  geo: '현재 위치',
  manual: '직접 지정한 위치',
  default: '기본 위치(서울역)',
}

/** 장소 검색 모달 */
export default function PlaceSearchModal({ reference, onSelect, onClose }) {
  const { location: referenceLocation, source, status: referenceStatus, setManualLocation } = reference
  const panelRef = useModal(onClose)
  // 기준 위치를 고르는 중인지
  const [pickingReference, setPickingReference] = useState(false)
  const title = pickingReference ? '내 위치 지정' : '장소 검색'
  const referenceLabel =
    referenceStatus === 'locating' ? REFERENCE_LOCATION_LABEL.locating : REFERENCE_LOCATION_LABEL[source]
  const [queryInput, setQueryInput] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState(null)
  const [emptyKeyword, setEmptyKeyword] = useState(false)
  const [page, setPage] = useState(0)

  const load = useCallback(() => searchPlaces(submittedQuery, page), [submittedQuery, page])
  const { data, status, error, reload } = useResource(load, submittedQuery !== null)

  const results = useMemo(() => {
    if (!data) return []
    return data.content
      .map(({ latitude, longitude, ...p }) => {
        const location = { lat: latitude, lng: longitude }
        return { ...p, location, distanceKm: haversineDistanceKm(referenceLocation, location) }
      })
      .sort((a, b) => a.distanceKm - b.distanceKm)
  }, [data, referenceLocation])

  const totalCount = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 0

  const handleSearch = (e) => {
    e.preventDefault()
    // 빈 검색어는 보내지 않는다
    const keyword = queryInput.trim()
    setEmptyKeyword(!keyword)
    if (!keyword) return
    // 같은 검색어로 다시 검색하면 직접 다시 읽는다
    if (keyword === submittedQuery && page === 0) {
      reload()
      return
    }
    setSubmittedQuery(keyword)
    setPage(0)
  }

  const handleSelect = (place) => {
    if (pickingReference) {
      setManualLocation(place.location)
      setPickingReference(false)
      setSubmittedQuery(null)
      setQueryInput('')
      return
    }
    onSelect({
      name: place.name,
      address: place.address,
      roadAddress: place.roadAddress ?? null,
      region: place.address,
      location: place.location,
      link: place.link ?? null,
    })
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        ref={panelRef}
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
            <button type="button" className="link-button" onClick={() => setPickingReference(true)}>
              위치 직접 지정
            </button>
          </p>
        )}

        <div className="place-search-body">
          {emptyKeyword && <p className="place-search-hint field-error">검색어를 입력해주세요.</p>}

          {!emptyKeyword && submittedQuery === null && (
            <p className="place-search-hint">검색어를 입력하고 검색해주세요.</p>
          )}

          {status === REQUEST_STATUS.LOADING && !data && (
            <p className="place-search-hint">검색 중…</p>
          )}

          {status === REQUEST_STATUS.ERROR && (
            <p className="place-search-hint field-error">
              {errorMessage(error, SEARCH_ERROR, '장소를 검색하지 못했어요.')}{' '}
              <button type="button" className="link-button" onClick={reload}>
                다시 시도
              </button>
            </p>
          )}

          {status === REQUEST_STATUS.COMPLETE && totalCount === 0 && (
            <p className="place-search-hint">검색 결과가 없어요.</p>
          )}

          {data && totalCount > 0 && (
            <>
              <p className="place-search-count">
                총 <strong>{totalCount}</strong>건 · 이 페이지 안에서 가까운 순
              </p>
              <ul className="place-search-list" aria-busy={status === REQUEST_STATUS.LOADING}>
                {results.map((p) => (
                  <li key={`${p.name}-${p.location.lat}-${p.location.lng}`}>
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

              {/* 결과가 상한에 닿으면 검색어를 좁히도록 안내한다 */}
              {totalCount >= PLACE_SEARCH_RESULT_LIMIT && (
                <p className="place-search-limit-note">
                  최대 {PLACE_SEARCH_RESULT_LIMIT}곳까지만 보여줘요. 찾는 곳이 없으면 지역명을 함께 넣어 검색해 보세요.
                </p>
              )}

              {totalPages > 1 && (
                <div
                  className="pagination"
                  role="navigation"
                  aria-label="검색 결과 페이지 네비게이션"
                >
                  <button
                    type="button"
                    className="page-btn"
                    disabled={page === 0}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    이전
                  </button>
                  {Array.from({ length: totalPages }, (_, i) => i).map((n) => (
                    <button
                      key={n}
                      type="button"
                      className={`page-btn${n === page ? ' active' : ''}`}
                      onClick={() => setPage(n)}
                    >
                      {n + 1}
                    </button>
                  ))}
                  <button
                    type="button"
                    className="page-btn"
                    disabled={page === totalPages - 1}
                    onClick={() => setPage((p) => p + 1)}
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
