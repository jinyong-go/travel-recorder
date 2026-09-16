import { useCallback, useState } from 'react'
import { getStoredMapMode, hasNaverMapClientId, storeMapMode } from '../config/mapSettings.js'

// 지도 표시 방식(새 창 / 페이지 내 임베드) 상태. 지도 버튼이 있는 화면에서 공용으로 쓴다.
export default function useMapMode() {
  const [mapMode, setMapMode] = useState(() => {
    const stored = getStoredMapMode()
    // Client ID 가 없으면 임베드가 불가능하므로 새 창 방식으로 되돌린다.
    return stored === 'embed' && hasNaverMapClientId ? 'embed' : 'link'
  })

  const changeMapMode = useCallback((key) => {
    setMapMode(key)
    storeMapMode(key)
  }, [])

  const isEmbed = mapMode === 'embed' && hasNaverMapClientId

  return { mapMode, changeMapMode, isEmbed }
}
