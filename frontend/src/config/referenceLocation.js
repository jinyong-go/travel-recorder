// SPECIFICATION.md 5.3 기준 위치(현재 위치) 처리
// 1) 브라우저 Geolocation 시도(클라이언트 한정, 서버 저장 금지)
// 2) 실패/거부 시 사용자가 직접 지정한 위치(로컬 저장소에만 보관)
// 3) 그마저 없으면 기본 위치 = 서울역

export const SEOUL_STATION = { lat: 37.5546, lng: 126.9706 }

const MANUAL_LOCATION_KEY = 'travel-recorder:manualLocation'

export const getStoredManualLocation = () => {
  try {
    const raw = localStorage.getItem(MANUAL_LOCATION_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const storeManualLocation = (location) => {
  try {
    localStorage.setItem(MANUAL_LOCATION_KEY, JSON.stringify(location))
  } catch {
    // 저장 공간을 사용할 수 없는 환경은 무시
  }
}

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
