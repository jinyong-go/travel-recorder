import { useCallback, useEffect, useState } from 'react'
import {
  SEOUL_STATION,
  getStoredManualLocation,
  requestGeolocation,
  storeManualLocation,
} from '../config/referenceLocation.js'

/** 거리 계산의 기준 위치 */
export default function useReferenceLocation() {
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
