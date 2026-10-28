/** 백엔드 API 주소 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(
  /\/$/,
  '',
)

/** 네이버 OAuth 로그인 주소 */
export const NAVER_LOGIN_URL = `${API_BASE_URL}/oauth2/authorization/naver`
