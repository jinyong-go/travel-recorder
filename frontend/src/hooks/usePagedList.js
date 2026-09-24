import { useCallback, useEffect, useState } from 'react'
import useLatestRequest from './useLatestRequest.js'
/**
 * 페이지 단위 목록 하나를 다루는 훅 — 첫 페이지 조회, "더 보기", 다시 읽기.
 *
 * 대기 초대·받은 초대·보낸 초대·받은 이력·보낸 이력 다섯 곳이 같은 모양이라 여기로 모았다
 * (명세 §5.8.3, §5.8.4).
 *
 * @param load `(page) => Promise<PageResponse>`. 바뀌면 처음부터 다시 읽는다
 * @param enabled false 면 호출하지 않고 빈 목록으로 둔다 (비로그인·탭이 닫힌 경우)
 * @returns total 은 서버가 알려준 전체 건수(`totalElements`)다. 첫 응답 전에는 undefined 다
 */
export default function usePagedList(load, enabled = true) {
  const [items, setItems] = useState([])
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [status, setStatus] = useState('loading')
  const [total, setTotal] = useState(undefined)
  const beginRequest = useLatestRequest()

  const read = useCallback(
    async (nextPage, append) => {
      const isLatest = beginRequest()
      setStatus('loading')
      try {
        const result = await load(nextPage)
        // 탭을 바꾸거나 다시 읽기를 누른 뒤 도착한 이전 응답은 버린다.
        if (!isLatest()) return
        setTotal(result.totalElements)
        setItems((prev) => (append ? [...prev, ...result.content] : result.content))
        setPage(result.page)
        // totalPages 는 0 일 수 있다(빈 목록). 그때는 다음 페이지가 없다.
        setHasNext(result.page + 1 < result.totalPages)
        setStatus('ready')
      } catch {
        if (isLatest()) setStatus('error')
      }
    },
    [load, beginRequest],
  )

  useEffect(() => {
    // 조회 대상이 바뀌면(초대함 받은 탭 ↔ 보낸 탭) 이전 목록의 건수를 들고 있지 않는다.
    setTotal(undefined)
    if (!enabled) {
      setItems([])
      setStatus('ready')
      return
    }
    read(0, false)
  }, [read, enabled])

  const loadMore = useCallback(() => read(page + 1, true), [read, page])
  const reload = useCallback(() => read(0, false), [read])

  return { items, status, hasNext, total, loadMore, reload }
}
