export const MAP_MODES = [
  { key: 'link', label: '새 창으로 열기', description: 'API 키 없이 구글 지도 앱/웹을 새 탭으로 엽니다.' },
  { key: 'embed', label: '페이지 내 지도 보기', description: '카드에서 바로 지도를 볼 수 있어요. Google Maps API 키가 필요합니다.' },
]

const STORAGE_KEY = 'travel-recorder:mapMode'

export const googleMapsApiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? ''

export const hasGoogleMapsApiKey = Boolean(googleMapsApiKey)

export const getStoredMapMode = () => {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export const storeMapMode = (mode) => {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // 저장 공간을 사용할 수 없는 환경은 무시
  }
}

const placeQuery = (place) => `${place.name} ${place.region}`

export const buildMapsSearchUrl = (place) =>
  `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(placeQuery(place))}`

export const buildMapsEmbedUrl = (place) =>
  `https://www.google.com/maps/embed/v1/place?key=${encodeURIComponent(googleMapsApiKey)}&q=${encodeURIComponent(placeQuery(place))}`
