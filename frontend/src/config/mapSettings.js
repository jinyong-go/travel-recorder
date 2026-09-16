export const MAP_MODES = [
  { key: 'link', label: '새 창으로 열기', description: 'API 키 없이 네이버 지도 앱/웹을 새 탭으로 엽니다.' },
  { key: 'embed', label: '페이지 내 지도 보기', description: '카드에서 바로 지도를 볼 수 있어요. 네이버 지도 Client ID 가 필요합니다.' },
]

const STORAGE_KEY = 'travel-recorder:mapMode'

export const naverMapClientId = import.meta.env.VITE_NAVER_MAP_CLIENT_ID ?? ''

export const hasNaverMapClientId = Boolean(naverMapClientId)

// 네이버 지도는 임베드 URL 을 제공하지 않으므로 SDK 를 로드해 직접 렌더링한다 (SPECIFICATION.md 5.7).
// 신규 발급 키는 ncpClientId 대신 ncpKeyId 를 요구할 수 있다. 지도가 인증 오류를 내면 이 쪽을 먼저 확인한다.
export const naverMapsSdkUrl = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpClientId=${encodeURIComponent(naverMapClientId)}&submodules=geocoder`

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

// 새 창으로 열 때는 네이버 지도 검색 결과로 보낸다 (SPECIFICATION.md 5.7).
// 좌표로 지도 중심을 지정하는 파라미터도 있지만 형식을 확인하지 못해 검색어만 넘긴다.
export const buildMapsSearchUrl = (place) =>
  `https://map.naver.com/p/search/${encodeURIComponent(placeQuery(place))}`
