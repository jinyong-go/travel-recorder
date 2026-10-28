import { apiFetch, apiPath } from './client.js'

/** 본인 계정 API */

/** 내 로그인 이력 */
export const fetchLoginHistory = (page) => apiFetch(apiPath`/api/auth/me/login-history?page=${page}`)
