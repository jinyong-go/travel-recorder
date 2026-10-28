import { apiFetch, apiPath } from './client.js'

/** 여행 API */

export const fetchTrips = (scope, sort, page) =>
  apiFetch(apiPath`/api/trips?scope=${scope}&sort=${sort}&page=${page}`)

export const fetchTrip = (tripId) => apiFetch(apiPath`/api/trips/${tripId}`)

/** 여행 만들기 */
export const createTrip = ({ sharedGroupIds, ...values }) =>
  apiFetch('/api/trips', { method: 'POST', body: { ...values, groupIds: sharedGroupIds } })

/** 여행 기본 정보 수정 */
export const updateTrip = (tripId, values) =>
  apiFetch(apiPath`/api/trips/${tripId}`, { method: 'PUT', body: values })

/** 여행 공개 범위 변경 */
export const changeTripVisibility = (tripId, visibility, groupIds) =>
  apiFetch(apiPath`/api/trips/${tripId}/visibility`, { method: 'PATCH', body: { visibility, groupIds } })

export const deleteTrip = (tripId) => apiFetch(apiPath`/api/trips/${tripId}`, { method: 'DELETE' })
