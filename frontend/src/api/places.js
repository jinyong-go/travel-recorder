import { apiFetch, apiPath } from './client.js'

/**
 * 장소 검색 API 호출.
 *
 * 백엔드가 네이버 지역 검색을 집계해 페이지로 잘라 준 `PageResponse` 를 그대로 돌려준다.
 * 기준 위치는 보내지 않는다 — 거리는 화면이 받은 페이지 안에서 계산한다.
 */
export const searchPlaces = (keyword, page) =>
  apiFetch(apiPath`/api/places/search?keyword=${keyword}&page=${page}`)
