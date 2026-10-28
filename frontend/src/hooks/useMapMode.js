import { useCallback, useState } from 'react'
import { getStoredMapMode, hasNaverMapClientId, storeMapMode } from '../config/mapSettings.js'

/** 지도 표시 방식 */
export default function useMapMode() {
  const [mapMode, setMapMode] = useState(() => {
    const stored = getStoredMapMode()
    return stored === 'embed' && hasNaverMapClientId ? 'embed' : 'link'
  })

  const changeMapMode = useCallback((key) => {
    setMapMode(key)
    storeMapMode(key)
  }, [])

  const isEmbed = mapMode === 'embed' && hasNaverMapClientId

  return { mapMode, changeMapMode, isEmbed }
}
