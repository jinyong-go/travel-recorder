/** 응답 대기를 흉내 내는 시간(ms) */
const MOCK_DELAY_MS = 250

/** 목업 값을 네트워크 응답처럼 늦게 돌려준다 */
export const mockResponse = (value) =>
  new Promise((resolve) => {
    setTimeout(() => resolve(structuredClone(value)), MOCK_DELAY_MS)
  })

/** 배열을 백엔드 PageResponse 모양으로 자른다 */
export function toPage(items, page, size) {
  return {
    content: items.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  }
}
