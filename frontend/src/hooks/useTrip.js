import { useEffect, useState } from 'react'
import { ApiError } from '../api/client.js'
import { fetchTrip } from '../api/trips.js'

/**
 * 여행 하나를 서버에서 읽는다. 여행 상세·기록 상세·기록 등록이 같은 모양으로 쓴다.
 *
 * `status` 는 'idle' | 'loading' | 'ready' | 'missing' | 'error' 다. `tripId` 가 비어 있으면
 * 조회하지 않고 'idle' 이다. 서버의 404 는 없는 여행과 볼 수 없는 여행을 구분하지 않으므로
 * 'missing' 하나로 받는다 (공통 명세 §2.6). 수정 응답으로 바로 갈아 끼울 수 있게 `setTrip` 을 준다.
 */
export default function useTrip(tripId) {
  const [trip, setTrip] = useState(null)
  const [status, setStatus] = useState(tripId ? 'loading' : 'idle')

  useEffect(() => {
    if (!tripId) {
      setTrip(null)
      setStatus('idle')
      return
    }

    // 여행을 빠르게 옮겨 다닐 때 늦게 도착한 이전 응답이 화면을 덮지 않게 한다.
    let cancelled = false
    setStatus('loading')
    fetchTrip(tripId)
      .then((result) => {
        if (cancelled) return
        setTrip(result)
        setStatus('ready')
      })
      .catch((error) => {
        if (cancelled) return
        setTrip(null)
        setStatus(error instanceof ApiError && error.status === 404 ? 'missing' : 'error')
      })

    return () => {
      cancelled = true
    }
  }, [tripId])

  return { trip, status, setTrip }
}
