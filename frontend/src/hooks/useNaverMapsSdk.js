import { useEffect, useState } from 'react'
import { hasNaverMapClientId, naverMapsSdkUrl } from '../config/mapSettings.js'

/** SDK 를 한 번만 불러오도록 공유하는 로드 Promise */
let loadPromise = null

const loadSdk = () => {
  if (loadPromise) return loadPromise

  loadPromise = new Promise((resolve, reject) => {
    if (window.naver?.maps) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = naverMapsSdkUrl
    script.async = true
    script.onload = () => (window.naver?.maps ? resolve() : reject(new Error('naver.maps 없음')))
    script.onerror = () => {
      // 실패하면 다시 시도할 수 있게 비운다
      loadPromise = null
      reject(new Error('네이버 지도 SDK 로드 실패'))
    }
    document.head.appendChild(script)
  })

  return loadPromise
}

/** 네이버 지도 SDK 로드 상태 */
export default function useNaverMapsSdk() {
  const [status, setStatus] = useState(() => {
    if (!hasNaverMapClientId) return 'disabled'
    return window.naver?.maps ? 'ready' : 'loading'
  })

  useEffect(() => {
    if (status !== 'loading') return
    let cancelled = false
    loadSdk()
      .then(() => !cancelled && setStatus('ready'))
      .catch(() => !cancelled && setStatus('error'))
    return () => {
      cancelled = true
    }
  }, [status])

  return status
}
