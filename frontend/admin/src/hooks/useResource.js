import { useCallback, useEffect, useState } from 'react'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import useLatestRequest from './useLatestRequest.js'

/** 서버 조회 하나와 그 상태 */
export default function useResource(load) {
  const [data, setData] = useState(null)
  const [status, setStatus] = useState(REQUEST_STATUS.LOADING)
  const beginRequest = useLatestRequest()

  const reload = useCallback(async () => {
    const isLatest = beginRequest()
    setStatus(REQUEST_STATUS.LOADING)
    try {
      const result = await load()
      if (!isLatest()) return
      setData(result)
      setStatus(REQUEST_STATUS.COMPLETE)
    } catch {
      if (!isLatest()) return
      setData(null)
      setStatus(REQUEST_STATUS.ERROR)
    }
  }, [load, beginRequest])

  useEffect(() => {
    reload()
  }, [reload])

  return { data, status, reload }
}
