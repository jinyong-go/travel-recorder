import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import useLatestRequest from './useLatestRequest.js'

/**
 * 페이지가 아닌 조회 하나를 다루는 훅 — 처음 조회, 다시 읽기, 늦게 도착한 응답 버리기.
 * 페이지 단위 목록은 `usePagedList` 가 맡는다.
 *
 * 서버의 404 는 `MISSING` 이다. 여행·기록은 없는 것과 볼 수 없는 것을 구분하지 않으므로 한 상태로
 * 받는다. 그 밖의 실패는 `ERROR` 이고, 코드별 안내가 필요한 화면은 `error` 를 본다.
 *
 * 다시 읽는 동안에도 이전 `data` 를 들고 있다. 저장 뒤 다시 읽을 때 화면이 "불러오는 중" 으로
 * 바뀌지 않게 하려면 `status` 대신 `data` 유무로 그린다.
 *
 * @param load `() => Promise`. 바뀌면 다시 읽는다
 * @param enabled false 면 호출하지 않고 `IDLE`, `data` 는 null 이다 (조회할 대상이 없는 경우)
 * @returns `setData` 는 수정 응답으로 다시 읽지 않고 갈아 끼울 때 쓴다
 */
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
    // 꺼지면 나가 있던 요청의 응답도 버린다.
    if (!enabled) {
      beginRequest()
      return
    }
    reload()
  }, [reload, enabled, beginRequest])

  // 꺼져 있는 동안의 값은 상태에 쓰지 않고 여기서 가린다. 다시 켜지면 곧바로 새로 읽는다.
  if (!enabled) return { data: null, status: REQUEST_STATUS.IDLE, error: null, reload, setData }
  return { data, status, error, reload, setData }
}
