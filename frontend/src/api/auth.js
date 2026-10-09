import { apiFetch, apiPath } from './client.js'

/**
 * 인증·본인 계정 API 호출 모음 (backend §4.2).
 *
 * 로그인·로그아웃·세션 조회는 로그인 상태와 함께 움직이므로 `AuthContext` 가 직접 부른다.
 * 여기에는 화면이 각자 읽는 본인 계정 조회만 둔다.
 */

/** 내 로그인 이력, 최신순 `PageResponse`. 대상은 언제나 세션의 사용자다. */
export const fetchLoginHistory = (page) => apiFetch(apiPath`/api/auth/me/login-history?page=${page}`)
