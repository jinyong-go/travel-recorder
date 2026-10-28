import { useCallback, useEffect, useState } from 'react'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import useLatestRequest from './useLatestRequest.js'
/** "더 보기"로 이어 붙이는 페이지 목록 */
export default function usePagedList(load, enabled = true) {
  const [items, setItems] = useState([])
  const [page, setPage] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [status, setStatus] = useState(REQUEST_STATUS.LOADING)
  const [total, setTotal] = useState(undefined)
  const beginRequest = useLatestRequest()

  const read = useCallback(
    async (nextPage, append) => {
      const isLatest = beginRequest()
      setStatus(REQUEST_STATUS.LOADING)
      try {
        const result = await load(nextPage)
        if (!isLatest()) return
        setTotal(result.totalElements)
        setItems((prev) => (append ? [...prev, ...result.content] : result.content))
        setPage(result.page)
        setHasNext(result.page + 1 < result.totalPages)
        setStatus(REQUEST_STATUS.COMPLETE)
      } catch {
        if (isLatest()) setStatus(REQUEST_STATUS.ERROR)
      }
    },
    [load, beginRequest],
  )

  useEffect(() => {
    setTotal(undefined)
    if (!enabled) {
      setItems([])
      setStatus(REQUEST_STATUS.COMPLETE)
      return
    }
    read(0, false)
  }, [read, enabled])

  const loadMore = useCallback(() => read(page + 1, true), [read, page])
  const reload = useCallback(() => read(0, false), [read])

  return { items, status, hasNext, total, loadMore, reload }
}
