import { readStorage, writeStorage } from '../utils/storage.js'

// 기준 위치(현재 위치) 처리
// 1) 브라우저 Geolocation 시도(클라이언트 한정, 서버 저장 금지)
// 2) 실패/거부 시 사용자가 직접 지정한 위치(로컬 저장소에만 보관)
// 3) 그마저 없으면 기본 위치 = 서울역

export const SEOUL_STATION = { lat: 37.5546, lng: 126.9706 }

const MANUAL_LOCATION_KEY = 'travel-recorder:manualLocation'

/** 직접 지정해 둔 위치(`{ lat, lng }`). 없거나 저장값이 깨졌으면 null 이다. */
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

/** 브라우저 위치를 한 번 요청한다. 지원하지 않거나 거부·시간 초과면 reject 한다. */
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
