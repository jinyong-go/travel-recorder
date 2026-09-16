import { useEffect, useState } from 'react'
import { hasNaverMapClientId, naverMapsSdkUrl } from '../config/mapSettings.js'

// SDK 스크립트는 문서당 한 번만 넣는다. 지도 모달을 여러 번 열어도 재사용된다.
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
      // 실패한 약속을 남겨 두면 다시 열어도 영원히 실패하므로 초기화한다.
      loadPromise = null
      reject(new Error('네이버 지도 SDK 로드 실패'))
    }
    document.head.appendChild(script)
  })

  return loadPromise
}

/** 네이버 지도 SDK 로드 상태: 'disabled' | 'loading' | 'ready' | 'error' */
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
