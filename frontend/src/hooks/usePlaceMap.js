import { useState } from 'react'
import { buildMapsSearchUrl } from '../config/mapSettings.js'

/**
 * 장소의 지도를 연다. 페이지 내 지도면 모달에 띄울 장소를 들고, 아니면 네이버 지도 검색을 새 창으로 연다.
 *
 * `isEmbed` 는 화면의 `useMapMode()` 값을 받는다. 헤더의 설정 메뉴와 같은 상태여야 바꾸는 즉시 반영된다.
 *
 * @returns `mapPlace` 모달에 띄울 장소(없으면 null), `openMap(place)`, `closeMap()`
 */
export default function usePlaceMap(isEmbed) {
  const [mapPlace, setMapPlace] = useState(null)

  const openMap = (place) => {
    if (isEmbed) setMapPlace(place)
    else window.open(buildMapsSearchUrl(place), '_blank', 'noopener,noreferrer')
  }

  return { mapPlace, openMap, closeMap: () => setMapPlace(null) }
}
