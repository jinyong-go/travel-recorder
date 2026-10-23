/** 백엔드 API 오리진. 개발 환경은 Spring Boot 기본 포트(8080)를 사용한다. */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(
  /\/$/,
  '',
)

/**
 * 네이버 OAuth 로그인 시작점. Spring Security 엔드포인트라 XHR 이 아니라 페이지 이동으로 진입한다.
 *
 * 지금은 임시 로그인(POST /api/auth/login)을 써서 쓰는 곳이 없다. OAuth 가 복구되면 로그인 화면의
 * 네이버 버튼(NaverIcon)이 이 주소로 보낸다.
 */
export const NAVER_LOGIN_URL = `${API_BASE_URL}/oauth2/authorization/naver`
