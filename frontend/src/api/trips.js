import { apiFetch } from './client.js'

/**
 * 여행 API 호출 모음 (backend §4.3).
 *
 * `groups.js` 와 같이 응답을 가공하지 않고 그대로 흘린다. 목록은 `PageResponse` 다 —
 * `{ content, page, size, totalElements, totalPages }`. 페이지 크기는 서버가 정한다 (backend §4.1).
 */

export const fetchTrips = (scope, sort, page) =>
  apiFetch(`/api/trips?scope=${scope}&sort=${sort}&page=${page}`)

export const fetchTrip = (tripId) => apiFetch(`/api/trips/${tripId}`)

/**
 * 여행 만들기. 폼의 `sharedGroupIds` 를 요청 이름 `groupIds` 로 보낸다.
 * `visibility` 가 GROUP 이 아니면 서버가 `groupIds` 를 무시한다 (backend §4.3.1).
 */
export const createTrip = ({ sharedGroupIds, ...values }) =>
  apiFetch('/api/trips', { method: 'POST', body: { ...values, groupIds: sharedGroupIds } })

/** 기본 정보 전체 갱신. 빠진 선택 항목은 비워진다. 공개 범위는 바뀌지 않는다 (backend §4.3). */
export const updateTrip = (tripId, values) =>
  apiFetch(`/api/trips/${tripId}`, { method: 'PUT', body: values })

/** 공개 범위 변경. 공유 그룹은 전체 교체다 — 빠진 그룹의 공유는 해제된다 (backend §4.3.1). */
export const changeTripVisibility = (tripId, visibility, groupIds) =>
  apiFetch(`/api/trips/${tripId}/visibility`, { method: 'PATCH', body: { visibility, groupIds } })

export const deleteTrip = (tripId) => apiFetch(`/api/trips/${tripId}`, { method: 'DELETE' })
