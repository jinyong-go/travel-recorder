import { readStorage, writeStorage } from '../utils/storage.js'

/** 지도 표시 방식과 네이버 지도 주소 */

export const MAP_MODES = [
  { key: 'link', label: '새 창으로 열기', description: 'API 키 없이 네이버 지도 앱/웹을 새 탭으로 열어요.' },
  { key: 'embed', label: '페이지 내 지도 보기', description: '카드에서 바로 지도를 볼 수 있어요. 네이버 지도 Client ID 가 필요해요.' },
]

const STORAGE_KEY = 'travel-recorder:mapMode'

export const naverMapClientId = import.meta.env.VITE_NAVER_MAP_CLIENT_ID ?? ''

export const hasNaverMapClientId = Boolean(naverMapClientId)

/** 네이버 지도 SDK 주소 */
export const naverMapsSdkUrl = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpClientId=${encodeURIComponent(naverMapClientId)}&submodules=geocoder`

export const getStoredMapMode = () => readStorage(STORAGE_KEY)

export const storeMapMode = (mode) => writeStorage(STORAGE_KEY, mode)

const placeQuery = (place) => `${place.name} ${place.region}`

/** 새 창으로 열 네이버 지도 검색 주소 */
export const buildMapsSearchUrl = (place) =>
  `https://map.naver.com/p/search/${encodeURIComponent(placeQuery(place))}`
