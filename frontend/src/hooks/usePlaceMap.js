import { useState } from 'react'
import { buildMapsSearchUrl } from '../config/mapSettings.js'

/** 장소 지도를 모달이나 새 창으로 연다 */
export default function usePlaceMap(isEmbed) {
  const [mapPlace, setMapPlace] = useState(null)

  const openMap = (place) => {
    if (isEmbed) setMapPlace(place)
    else window.open(buildMapsSearchUrl(place), '_blank', 'noopener,noreferrer')
  }

  return { mapPlace, openMap, closeMap: () => setMapPlace(null) }
}
