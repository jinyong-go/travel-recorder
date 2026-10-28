import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import useLatestRequest from './useLatestRequest.js'

/** 서버 조회 하나와 그 상태 */
export default function useResource(load, enabled = true) {
  const [data, setData] = useState(null)
  const [status, setStatus] = useState(REQUEST_STATUS.LOADING)
  const [error, setError] = useState(null)
  const beginRequest = useLatestRequest()

  const reload = useCallback(async () => {
    const isLatest = beginRequest()
    setStatus(REQUEST_STATUS.LOADING)
    try {
      const result = await load()
      if (!isLatest()) return
      setData(result)
      setError(null)
      setStatus(REQUEST_STATUS.COMPLETE)
    } catch (err) {
      if (!isLatest()) return
      setData(null)
      setError(err)
      setStatus(err instanceof ApiError && err.status === 404 ? REQUEST_STATUS.MISSING : REQUEST_STATUS.ERROR)
    }
  }, [load, beginRequest])

  useEffect(() => {
    if (!enabled) {
      beginRequest()
      return
    }
    reload()
  }, [reload, enabled, beginRequest])

  if (!enabled) return { data: null, status: REQUEST_STATUS.IDLE, error: null, reload, setData }
  return { data, status, error, reload, setData }
}
