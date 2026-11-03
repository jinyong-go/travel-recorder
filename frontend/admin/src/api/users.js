import { MOCK_USERS } from '../mock/data.js'
import { mockResponse, toPage } from './mock.js'

/** 한 번에 읽는 사용자 수 */
export const USER_PAGE_SIZE = 20

/**
 * 사용자 목록을 최근 가입순으로 읽는다. 이름·이메일에 검색어가 들어간 사람만 남긴다.
 * 백엔드 API 가 없어 목업을 돌려준다.
 */
export function fetchUsers(query, page) {
  const keyword = query.trim().toLowerCase()
  const matched = keyword
    ? MOCK_USERS.filter((u) => u.name.toLowerCase().includes(keyword) || u.email.includes(keyword))
    : MOCK_USERS
  return mockResponse(toPage(matched, page, USER_PAGE_SIZE))
}
