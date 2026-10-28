import { readStorage, writeStorage } from '../utils/storage.js'

/** 거리 계산의 기준 위치 */

export const SEOUL_STATION = { lat: 37.5546, lng: 126.9706 }

const MANUAL_LOCATION_KEY = 'travel-recorder:manualLocation'

/** 직접 지정한 위치. 없으면 null. */
export const getStoredManualLocation = () => {
  const raw = readStorage(MANUAL_LOCATION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const storeManualLocation = (location) => writeStorage(MANUAL_LOCATION_KEY, JSON.stringify(location))

/** 브라우저 위치를 요청한다 */
export const requestGeolocation = () =>
  new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('geolocation-unsupported'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({ lat: position.coords.latitude, lng: position.coords.longitude })
      },
      (error) => reject(error),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 5 * 60 * 1000 },
    )
  })
