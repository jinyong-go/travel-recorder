import { apiFetch, apiPath } from './client.js'

/** 기록·사진 API */

/** 여행 하나의 기록 목록 */
export const fetchTripRecords = (tripId, { lat, lng }, page) =>
  apiFetch(apiPath`/api/records?tripId=${tripId}&sort=OLDEST&lat=${lat}&lng=${lng}&page=${page}`)

export const fetchRecord = (recordId) => apiFetch(apiPath`/api/records/${recordId}`)

export const createRecord = (body) => apiFetch('/api/records', { method: 'POST', body })

/** 기록 수정 */
export const updateRecord = (recordId, body) =>
  apiFetch(apiPath`/api/records/${recordId}`, { method: 'PUT', body })

export const deleteRecord = (recordId) => apiFetch(apiPath`/api/records/${recordId}`, { method: 'DELETE' })

/** 사진 여러 장을 올린다 */
export const uploadPhotos = (recordId, files) => {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  return apiFetch(apiPath`/api/records/${recordId}/photos`, { method: 'POST', body: form })
}

export const deletePhoto = (recordId, photoId) =>
  apiFetch(apiPath`/api/records/${recordId}/photos/${photoId}`, { method: 'DELETE' })
