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

const readCookie = (name) =>
  document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.slice(name.length + 1)

const WRITE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE']

/**
 * 백엔드 호출 공통 래퍼. 응답 본문이 있으면 JSON 으로 돌려주고, 실패하면 `ApiError` 를 던진다.
 *
 * 세션 쿠키 인증이라 자격 증명을 항상 포함하고, 쓰기 요청에는 서버가 내려준 XSRF 토큰을
 * 헤더로 돌려보낸다. 헤더가 없으면 서버가 거부한다 (공통 명세 §6.1).
 */
export async function apiFetch(path, { method = 'GET', body } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  if (WRITE_METHODS.includes(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  // 204 와 빈 본문을 구분하지 않고 null 로 통일한다. 호출부가 매번 확인할 것이 하나 줄어든다.
  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new ApiError({ ...(payload ?? {}), status: response.status })
  }
  return payload
}
