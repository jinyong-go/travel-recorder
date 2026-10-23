import { readStorage, writeStorage } from '../utils/storage.js'

export const MAP_MODES = [
  { key: 'link', label: '새 창으로 열기', description: 'API 키 없이 네이버 지도 앱/웹을 새 탭으로 열어요.' },
  { key: 'embed', label: '페이지 내 지도 보기', description: '카드에서 바로 지도를 볼 수 있어요. 네이버 지도 Client ID 가 필요해요.' },
]

const STORAGE_KEY = 'travel-recorder:mapMode'

export const naverMapClientId = import.meta.env.VITE_NAVER_MAP_CLIENT_ID ?? ''

export const hasNaverMapClientId = Boolean(naverMapClientId)

// 네이버 지도는 임베드 URL 을 제공하지 않으므로 SDK 를 로드해 직접 렌더링한다.
// 신규 발급 키는 ncpClientId 대신 ncpKeyId 를 요구할 수 있다. 지도가 인증 오류를 내면 이 쪽을 먼저 확인한다.
export const naverMapsSdkUrl = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpClientId=${encodeURIComponent(naverMapClientId)}&submodules=geocoder`

export const getStoredMapMode = () => readStorage(STORAGE_KEY)

export const storeMapMode = (mode) => writeStorage(STORAGE_KEY, mode)

const placeQuery = (place) => `${place.name} ${place.region}`

// 새 창으로 열 때는 네이버 지도 검색 결과로 보낸다.
// 좌표로 지도 중심을 지정하는 파라미터도 있지만 형식을 확인하지 못해 검색어만 넘긴다.
export const buildMapsSearchUrl = (place) =>
  `https://map.naver.com/p/search/${encodeURIComponent(placeQuery(place))}`
