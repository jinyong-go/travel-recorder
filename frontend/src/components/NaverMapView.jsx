import { useEffect, useRef } from 'react'
import useNaverMapsSdk from '../hooks/useNaverMapsSdk.js'
import './NaverMapView.css'

const MESSAGES = {
  disabled: '네이버 지도 Client ID 가 없어 지도를 표시할 수 없어요.',
  loading: '지도를 불러오는 중...',
  error: '지도를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
  noLocation: '좌표 정보가 없어 지도를 표시할 수 없어요.',
}

// 네이버 지도 SDK v3 로 지도를 그리고 여행지 좌표에 마커를 찍는다 (SPECIFICATION.md 5.7).
export default function NaverMapView({ place, zoom = 15 }) {
  const status = useNaverMapsSdk()
  const containerRef = useRef(null)
  const { lat, lng } = place.location ?? {}
  const hasLocation = lat != null && lng != null

  useEffect(() => {
    if (status !== 'ready' || !hasLocation || !containerRef.current) return

    const { naver } = window
    const center = new naver.maps.LatLng(lat, lng)
    const map = new naver.maps.Map(containerRef.current, { center, zoom })
    const marker = new naver.maps.Marker({ position: center, map, title: place.name })

    return () => {
      marker.setMap(null)
      map.destroy()
    }
  }, [status, hasLocation, lat, lng, zoom, place.name])

  const message = !hasLocation ? MESSAGES.noLocation : MESSAGES[status]
  if (message) {
    return <p className="naver-map-message">{message}</p>
  }

  return <div className="naver-map-view" ref={containerRef} role="application" aria-label={`${place.name} 지도`} />
}
