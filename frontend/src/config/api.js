// 백엔드 API 오리진. 개발 환경은 Spring Boot 기본 포트(8080)를 사용한다.
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(
  /\/$/,
  '',
)

// 네이버 OAuth 로그인 시작점. Spring Security 가 제공하는 엔드포인트로,
// XHR 이 아니라 브라우저 전체 이동(window.location.href)으로 진입해야 한다.
export const NAVER_LOGIN_URL = `${API_BASE_URL}/oauth2/authorization/naver`
