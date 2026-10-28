import { apiFetch, apiPath } from './client.js'

/** 장소 검색 API */
export const searchPlaces = (keyword, page) =>
  apiFetch(apiPath`/api/places/search?keyword=${keyword}&page=${page}`)
