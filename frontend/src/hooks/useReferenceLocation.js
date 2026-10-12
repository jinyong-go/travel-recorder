import { useCallback, useEffect, useState } from 'react'
import {
  SEOUL_STATION,
  getStoredManualLocation,
  requestGeolocation,
  storeManualLocation,
} from '../config/referenceLocation.js'

/**
 * 장소 거리 계산의 기준 위치.
 *
 * 직접 지정한 위치가 저장돼 있으면 그것을 쓰고, 없으면 브라우저 위치를 한 번 요청한다. 위치를
 * 받기 전이거나 거부되면 기본 위치(서울역)다. 위치는 서버로 보내 저장하지 않는다.
 *
 * @returns `location` `{ lat, lng }`, `source` `'manual' | 'geo' | 'default'`,
 *   `status` `'idle' | 'locating' | 'granted' | 'denied'`,
 *   `setManualLocation(coords)` 직접 지정한 위치로 바꾸고 로컬 저장소에 남긴다
 */
export default function useReferenceLocation() {
  // 저장값은 처음 한 번만 읽는다. 렌더링마다 저장소를 읽을 이유가 없다.
  const [storedManual] = useState(getStoredManualLocation)
  const [location, setLocation] = useState(storedManual ?? SEOUL_STATION)
  const [source, setSource] = useState(storedManual ? 'manual' : 'default')
  const [status, setStatus] = useState(storedManual ? 'idle' : 'locating')

  useEffect(() => {
    if (storedManual) return
    let cancelled = false
    requestGeolocation()
      .then((coords) => {
        if (cancelled) return
        setLocation(coords)
        setSource('geo')
        setStatus('granted')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('denied')
      })
    return () => {
      cancelled = true
    }
  }, [storedManual])

  const setManualLocation = useCallback((coords) => {
    storeManualLocation(coords)
    setLocation(coords)
    setSource('manual')
    setStatus('granted')
  }, [])

  return { location, source, status, setManualLocation }
}
