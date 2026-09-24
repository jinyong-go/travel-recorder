import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, apiFetch, setUnauthorizedHandler } from '../api/client.js'

const AuthContext = createContext(null)

/**
 * 로그인 상태. 프론트엔드에서 실제 백엔드를 호출하는 유일한 영역이다 (명세 §10.2).
 *
 * 부팅 시 `GET /api/auth/me` 로 세션을 확인한다. 새로고침해도 로그인이 유지되는 것은
 * 이 조회 덕분이며(명세 §2.1), 이 요청이 XSRF 토큰 쿠키도 함께 받아 와 이후 쓰기 요청의
 * 전제를 만든다 (공통 명세 §6.1).
 *
 * 로그인 상태에서 받은 `401` 은 세션 만료로 보고 로그인 화면으로 보낸다 (명세 §2.1).
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  // loading 을 따로 두는 이유는 비로그인과 "아직 모름" 이 다르기 때문이다.
  // 구분하지 않으면 새로고침 직후 로그인 상태가 잠깐 비로그인으로 보인다.
  const [status, setStatus] = useState('loading')
  const navigate = useNavigate()

  // 만료 핸들러는 한 번만 등록하므로 최신 status 를 ref 로 읽는다.
  const statusRef = useRef(status)
  useEffect(() => {
    statusRef.current = status
  }, [status])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      // 부팅 시 본인 정보 조회와 로그인 요청의 401 은 만료가 아니다.
      // 로그인 상태였을 때만 만료로 본다 (명세 §2.1).
      if (statusRef.current !== 'authenticated') return
      // 동시에 나간 요청 여럿이 401 을 받아도 한 번만 처리한다.
      statusRef.current = 'anonymous'
      setUser(null)
      setStatus('anonymous')
      // useLocation 을 쓰면 화면을 옮길 때마다 프로바이더가 다시 그려지므로 window.location 을 읽는다.
      const { pathname, search } = window.location
      navigate('/login?error=session_expired', {
        replace: true,
        state: { from: pathname + search },
      })
    })
    return () => setUnauthorizedHandler(null)
  }, [navigate])

  useEffect(() => {
    let cancelled = false

    apiFetch('/api/auth/me')
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setStatus('authenticated')
      })
      .catch(() => {
        // 401 도 서버가 죽은 것도 화면에서는 같다 — 로그인하지 않은 상태로 그린다.
        if (cancelled) return
        setUser(null)
        setStatus('anonymous')
      })

    return () => {
      cancelled = true
    }
  }, [])

  /** 로그인에 성공하면 사용자 정보를 반환하고, 실패하면 안내 문구를 담아 되던진다. */
  const login = useCallback(async (username, password) => {
    try {
      const me = await apiFetch('/api/auth/login', {
        method: 'POST',
        body: { username, password },
      })
      setUser(me)
      setStatus('authenticated')
      return me
    } catch (error) {
      setUser(null)
      setStatus('anonymous')
      throw error instanceof ApiError ? error : new ApiError({})
    }
  }, [])

  /** 서버 세션을 버린다. 호출이 실패해도 화면은 비로그인으로 내린다 — 여기서 붙잡아 둘 이유가 없다. */
  const logout = useCallback(async () => {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' })
    } finally {
      setUser(null)
      setStatus('anonymous')
    }
  }, [])

  const value = useMemo(
    () => ({ user, status, isLoggedIn: status === 'authenticated', login, logout }),
    [user, status, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.')
  return context
}
