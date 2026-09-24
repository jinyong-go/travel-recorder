import { useCallback, useEffect, useState } from 'react'
/**
 * 페이지 단위 목록 하나를 다루는 훅 — 첫 페이지 조회, "더 보기", 다시 읽기.
 *
 * 대기 초대·받은 초대·보낸 초대·받은 이력·보낸 이력 다섯 곳이 같은 모양이라 여기로 모았다
 * (명세 §5.8.3, §5.8.4).
 *
 * @param load `(page) => Promise<PageResponse>`. 바뀌면 처음부터 다시 읽는다
 * @param enabled false 면 호출하지 않고 빈 목록으로 둔다 (비로그인·탭이 닫힌 경우)
 */
export default function usePagedList(load, enabled = true) {
  const [items, setItems] = useState([])
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [status, setStatus] = useState('loading')

  const read = useCallback(
    async (nextPage, append) => {
      setStatus('loading')
      try {
        const result = await load(nextPage)
        setItems((prev) => (append ? [...prev, ...result.content] : result.content))
        setPage(result.page)
        // totalPages 는 0 일 수 있다(빈 목록). 그때는 다음 페이지가 없다.
        setHasNext(result.page + 1 < result.totalPages)
        setStatus('ready')
      } catch {
        setStatus('error')
      }
    },
    [load],
  )

  useEffect(() => {
    if (!enabled) {
      setItems([])
      setStatus('ready')
      return
    }
    read(0, false)
  }, [read, enabled])

  const loadMore = useCallback(() => read(page + 1, true), [read, page])
  const reload = useCallback(() => read(0, false), [read])

  return { items, status, hasNext, loadMore, reload }
}
