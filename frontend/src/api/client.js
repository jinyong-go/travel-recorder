import { API_BASE_URL } from '../config/api.js'

/**
 * 백엔드 오류 응답(`{ code, message, status }`)을 그대로 실어 나르는 예외.
 * 화면은 문구가 아니라 `code` 로 분기한다 (공통 명세 §6.1).
 */
export class ApiError extends Error {
  constructor({ code, message, status }) {
    super(message ?? '요청을 처리하지 못했습니다.')
    this.name = 'ApiError'
    this.code = code ?? 'INTERNAL_ERROR'
    this.status = status
  }
}

const WRITE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE']

// 토큰 쿠키가 HttpOnly 라 읽을 수 없으므로 세션 조회로 받아 둔다. 메모리에만 둔다 —
// localStorage 에 두면 탭·재방문을 넘어 남는다 (명세 §2.1).
let csrfToken = null

/**
 * 세션 상태를 조회해 로그인 여부를 돌려주고, CSRF 토큰은 이 모듈에 보관한다.
 * 비로그인이어도 성공 응답이며, 실패하면 `ApiError` 를 던진다.
 */
export async function fetchSession() {
  const session = await apiFetch('/api/auth/session')
  csrfToken = session.csrfToken
  return session.authenticated
}

/**
 * 들고 있는 CSRF 토큰을 버린다. 로그인·로그아웃 뒤 서버가 토큰을 비우므로 부른다
 * (공통 명세 §6.1). 다음 쓰기 요청 전에 세션 조회로 새 토큰을 받는다.
 */
export function clearCsrfToken() {
  csrfToken = null
}

/**
 * 서버가 주는 파일 URL(`/api/files/...`)을 화면에서 쓸 수 있는 주소로 바꾼다. 경로가 백엔드
 * 기준인데 화면은 다른 오리진에서 뜨므로 앞에 백엔드 주소를 붙인다. 없으면 null 이다.
 */
export const fileUrl = (path) => (path ? `${API_BASE_URL}${path}` : null)

/**
 * API 경로를 만드는 태그 함수. 끼워 넣는 값을 전부 URL 인코딩한다.
 *
 * 경로 값의 출처가 화면 URL(`useParams`)일 수 있어서다. 라우터는 `%2F` 를 `/` 로 풀어 주므로
 * 그대로 붙이면 `/groups/..%2Ftrips%2F7` 같은 링크 하나로 로그인 쿠키가 실린 요청을 다른 API 로
 * 보낼 수 있다 (클라이언트 측 경로 조작). 고정 문자열 부분은 건드리지 않는다.
 */
export const apiPath = (strings, ...values) =>
  strings.reduce((path, part, i) => path + encodeURIComponent(values[i - 1]) + part)

let unauthorizedHandler = null

/**
 * `401` 응답을 받았을 때 부를 함수를 등록한다. `AuthContext` 만 쓴다 (명세 §2.1).
 * client 가 컨텍스트를 import 하면 순환 의존이 생기므로, 거꾸로 등록받는다.
 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

/**
 * 백엔드 호출 공통 래퍼. 응답 본문이 있으면 JSON 으로 돌려주고, 실패하면 `ApiError` 를 던진다.
 *
 * 세션 쿠키 인증이라 자격 증명을 항상 포함하고, 쓰기 요청에는 세션 조회로 받은 CSRF 토큰을
 * 헤더로 돌려보낸다. 헤더가 없으면 서버가 거부한다 (공통 명세 §6.1).
 */
export async function apiFetch(path, { method = 'GET', body } = {}) {
  const headers = {}
  // 사진 업로드는 멀티파트다. 경계(boundary)는 브라우저가 정하므로 Content-Type 을 비워 둔다.
  const isForm = body instanceof FormData
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'

  // 토큰이 없는 것은 부팅 조회가 실패했거나 로그인·로그아웃 직후다. 여기서 받지 않으면
  // 새로고침 전까지 모든 쓰기가 403 이 된다.
  if (WRITE_METHODS.includes(method)) {
    if (!csrfToken) await fetchSession()
    headers['X-XSRF-TOKEN'] = csrfToken
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined || isForm ? body : JSON.stringify(body),
  })

  // 세션 만료는 본문 형식과 무관하게 처리한다. 앞단 프록시가 HTML 401 을 돌려줘도 놓치지 않게
  // 파싱보다 먼저 본다.
  if (response.status === 401) unauthorizedHandler?.()

  // 204 와 빈 본문을 구분하지 않고 null 로 통일한다. 호출부가 매번 확인할 것이 하나 줄어든다.
  // JSON 이 아닌 응답(Tomcat·프록시가 앞단에서 거부한 HTML 등)은 파싱 오류가 그대로 나가게 둔다.
  // ApiError 로 감싸면 화면이 준비한 실패 문구 대신 공통 기본 문구가 보인다.
  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) throw new ApiError({ ...(payload ?? {}), status: response.status })
  return payload
}
