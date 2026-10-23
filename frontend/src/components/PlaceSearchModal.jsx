import { useCallback, useMemo, useState } from 'react'
import useModal from '../hooks/useModal.js'
import useResource from '../hooks/useResource.js'
import { searchPlaces } from '../api/places.js'
import { errorMessage } from '../api/client.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import { CloseIcon, SearchIcon } from './icons.jsx'
import { haversineDistanceKm } from '../utils/geo.js'
import './PlaceSearchModal.css'

const SEARCH_ERROR = {
  PLACE_SEARCH_UNAVAILABLE: '지금은 장소 검색을 할 수 없어요. 잠시 후 다시 시도해주세요.',
}

/** 검색 결과 거리의 기준이 무엇인지. 확인 중이면 출처보다 그 사실을 먼저 알린다. */
const REFERENCE_LOCATION_LABEL = {
  locating: '현재 위치 확인 중…',
  geo: '현재 위치',
  manual: '직접 지정한 위치',
  default: '기본 위치(서울역)',
}

/**
 * 우편번호 검색 방식의 장소 검색 모달. 백엔드가 네이버 지역 검색을 집계해 페이지로 준다.
 *
 * 기준 위치는 서버로 보내지 않으므로 거리순 정렬은 받은 페이지 안에서만 이뤄진다. 원본 API 의
 * 검색어당 건수 상한 때문에 결과가 적게 와도 오류가 아니다.
 */
export default function PlaceSearchModal({ reference, onSelect, onClose }) {
  const { location: referenceLocation, source, status: referenceStatus, setManualLocation } = reference
  const panelRef = useModal(onClose)
  // true 면 고른 장소를 기록에 넣지 않고 기준 위치로 저장한다.
  const [pickingReference, setPickingReference] = useState(false)
  const title = pickingReference ? '내 위치 지정' : '장소 검색'
  const referenceLabel =
    referenceStatus === 'locating' ? REFERENCE_LOCATION_LABEL.locating : REFERENCE_LOCATION_LABEL[source]
  const [queryInput, setQueryInput] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState(null)
  const [emptyKeyword, setEmptyKeyword] = useState(false)
  // 서버 페이지 번호라 0 부터다. 화면에는 1 을 더해 보인다.
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
    // 빈 검색어는 외부 API 호출 한도만 쓰므로 보내지 않고 안내만 한다.
    const keyword = queryInput.trim()
    setEmptyKeyword(!keyword)
    if (!keyword) return
    // 같은 검색어로 다시 검색하면 load 가 바뀌지 않아 요청이 나가지 않는다. 직접 다시 읽는다.
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
      // 지정을 마치면 장소 검색으로 돌아간다. 새 기준으로 다시 검색하도록 결과를 비운다.
      setPickingReference(false)
      setSubmittedQuery(null)
      setQueryInput('')
      return
    }
    onSelect({
      name: place.name,
      address: place.address,
      roadAddress: place.roadAddress ?? null,
      // 검색 결과에는 지역명이 따로 없어 기존 기록(placeOf)처럼 주소를 대신 쓴다.
      region: place.address,
      location: place.location,
      // 네이버 지역 검색 결과의 원본 링크. 있을 때만 등록 요청의 externalLink 로 넘어간다.
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
              {/* 페이지를 넘기는 동안 이전 결과를 남겨 두되, 바뀌는 중임을 알린다. */}
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
