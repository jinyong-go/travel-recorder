import { useCallback } from 'react'
import { fetchTrip } from '../api/trips.js'
import useResource from './useResource.js'

/**
 * 여행 하나를 서버에서 읽는다. 여행 상세·기록 상세·기록 등록이 같은 모양으로 쓴다.
 *
 * `status` 는 `REQUEST_STATUS` 값이다. `tripId` 가 비어 있으면 조회하지 않고 `IDLE` 이다.
 * 서버의 404 는 없는 여행과 볼 수 없는 여행을 구분하지 않으므로 `MISSING` 하나로 받는다.
 * 수정 응답으로 바로 갈아 끼울 수 있게 `setTrip` 을 준다.
 */
export default function useTrip(tripId) {
  const load = useCallback(() => fetchTrip(tripId), [tripId])
  const { data: trip, status, setData: setTrip } = useResource(load, Boolean(tripId))
  return { trip, status, setTrip }
}
