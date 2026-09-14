import { useCallback, useEffect, useState } from 'react'
import {
  SEOUL_STATION,
  getStoredManualLocation,
  requestGeolocation,
  storeManualLocation,
} from '../config/referenceLocation.js'

// 기준 위치 확보 상태를 관리하는 훅. SPECIFICATION.md 5.3 참고.
export default function useReferenceLocation() {
  const storedManual = getStoredManualLocation()
  const [location, setLocation] = useState(storedManual ?? SEOUL_STATION)
  const [source, setSource] = useState(storedManual ? 'manual' : 'default')
  const [status, setStatus] = useState('idle')

  useEffect(() => {
    if (storedManual) return
    let cancelled = false
    setStatus('locating')
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const setManualLocation = useCallback((coords) => {
    storeManualLocation(coords)
    setLocation(coords)
    setSource('manual')
    setStatus('granted')
  }, [])

  return { location, source, status, setManualLocation }
}
