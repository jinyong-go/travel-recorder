import { apiFetch } from './client.js'

/**
 * 기록·사진 API 호출 모음 (backend §4.4, §4.6).
 *
 * `trips.js` 와 같이 응답을 가공하지 않고 그대로 흘린다. 목록은 `PageResponse` 다.
 */

/**
 * 한 여행의 하위 기록. **scope 를 싣지 않는다** — 열람자는 남의 여행이 공유인지 공개인지 몰라
 * 고를 수 없고, tripId 만 주면 서버가 그 여행을 볼 수 있는지로 판정한다 (backend §4.4.1).
 * 등록순이며, 기준 좌표를 함께 보내 거리를 받는다.
 */
export const fetchTripRecords = (tripId, { lat, lng }, page) =>
  apiFetch(`/api/records?tripId=${tripId}&sort=oldest&lat=${lat}&lng=${lng}&page=${page}`)

export const fetchRecord = (recordId) => apiFetch(`/api/records/${recordId}`)

export const createRecord = (body) => apiFetch('/api/records', { method: 'POST', body })

/**
 * 전체 갱신이다. 빠진 선택 항목은 비워진다 — 화면에 입력칸이 없는 태그도 기존 값을 그대로
 * 실어 보내야 지워지지 않는다 (backend §4.4).
 */
export const updateRecord = (recordId, body) =>
  apiFetch(`/api/records/${recordId}`, { method: 'PUT', body })

export const deleteRecord = (recordId) => apiFetch(`/api/records/${recordId}`, { method: 'DELETE' })

/** 여러 장을 한 요청에 올린다. 올라간 사진 목록(`{ id, url }`)을 돌려준다. */
export const uploadPhotos = (recordId, files) => {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  return apiFetch(`/api/records/${recordId}/photos`, { method: 'POST', body: form })
}

export const deletePhoto = (recordId, photoId) =>
  apiFetch(`/api/records/${recordId}/photos/${photoId}`, { method: 'DELETE' })
