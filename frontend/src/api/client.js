import { API_BASE_URL } from '../config/api.js'

/** 백엔드 오류 응답을 담는 예외 */
export class ApiError extends Error {
  constructor({ code, message, status }) {
    super(message ?? '요청을 처리하지 못했습니다.')
    this.name = 'ApiError'
    this.code = code ?? 'INTERNAL_ERROR'
    this.status = status
  }
}

const WRITE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE']

/** 쓰기 요청에 붙일 CSRF 토큰 */
let csrfToken = null

/** 세션 상태를 조회하고 CSRF 토큰을 받아 둔다 */
export async function fetchSession() {
  const session = await apiFetch('/api/auth/session')
  csrfToken = session.csrfToken
  return session.authenticated
}

/** 들고 있는 CSRF 토큰을 버린다 */
export function clearCsrfToken() {
  csrfToken = null
}

/** 서버가 준 파일 경로를 화면에서 쓸 URL 로 바꾼다 */
export const fileUrl = (path) => (path ? `${API_BASE_URL}${path}` : null)

/** API 경로를 만든다. 경로 조작을 막기 위해 끼워 넣는 값은 URL 인코딩한다. */
export const apiPath = (strings, ...values) =>
  strings.reduce((path, part, i) => path + encodeURIComponent(values[i - 1]) + part)

/** 실패한 요청에 맞는 화면 문구를 고른다 */
export const errorMessage = (err, messages, fallback) =>
  (err instanceof ApiError ? messages[err.code] : undefined) ?? fallback

let unauthorizedHandler = null

/** 세션 만료(401) 때 부를 함수를 등록한다 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

/** 백엔드 API 를 호출한다. 실패하면 ApiError 를 던진다. */
export async function apiFetch(path, { method = 'GET', body, withStatus = false } = {}) {
  const headers = {}
  // 멀티파트 요청은 Content-Type 을 브라우저가 정한다
  const isForm = body instanceof FormData
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'

  // 토큰이 없으면 먼저 받아 온다
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

  // 세션 만료 처리
  if (response.status === 401) unauthorizedHandler?.()

  // 빈 본문은 null 로 돌려준다
  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) throw new ApiError({ ...(payload ?? {}), status: response.status })
  return withStatus ? { data: payload, status: response.status } : payload
}
