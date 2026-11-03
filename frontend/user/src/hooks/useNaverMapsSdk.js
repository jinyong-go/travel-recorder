import { useEffect, useState } from 'react'
import { hasNaverMapClientId, naverMapsSdkUrl } from '../config/mapSettings.js'

/** SDK 를 한 번만 불러오도록 공유하는 로드 Promise */
let loadPromise = null

let authFailed = false
const authFailureListeners = new Set()

const loadSdk = () => {
  if (loadPromise) return loadPromise

  loadPromise = new Promise((resolve, reject) => {
    if (window.naver?.maps) {
      resolve()
      return
    }
    // 키나 등록된 서비스 URL 이 맞지 않으면 SDK 가 로드된 뒤 이 전역 함수를 부른다
    window.navermap_authFailure = () => {
      authFailed = true
      authFailureListeners.forEach((listener) => listener())
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
    if (authFailed) return 'error'
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

  useEffect(() => {
    if (status === 'disabled' || status === 'error') return
    const onAuthFailure = () => setStatus('error')
    authFailureListeners.add(onAuthFailure)
    return () => {
      authFailureListeners.delete(onAuthFailure)
    }
  }, [status])

  return status
}
