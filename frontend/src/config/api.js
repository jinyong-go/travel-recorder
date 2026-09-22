// 백엔드 API 오리진. 개발 환경은 Spring Boot 기본 포트(8080)를 사용한다.
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(
  /\/$/,
  '',
)

// 네이버 OAuth 로그인 시작점. Spring Security 가 제공하는 엔드포인트로,
// XHR 이 아니라 브라우저 전체 이동(window.location.href)으로 진입해야 한다.
//
// 현재는 임시 아이디/비밀번호 로그인(POST /api/auth/login)을 쓰므로 호출되지 않는다.
// 백엔드 OAuth 가 복구되면 로그인 화면의 주석과 함께 되살린다 (명세 §2.1).
export const NAVER_LOGIN_URL = `${API_BASE_URL}/oauth2/authorization/naver`
