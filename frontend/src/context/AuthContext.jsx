import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, clearCsrfToken, fetchSession, setUnauthorizedHandler } from '../api/client.js'
import { AUTH_STATUS } from './authStatus.js'

const AuthContext = createContext(null)

/** 로그인 상태와 본인 정보를 제공한다 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState(AUTH_STATUS.LOADING)
  const navigate = useNavigate()

  const statusRef = useRef(status)
  useEffect(() => {
    statusRef.current = status
  }, [status])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      // 로그인 상태에서 받은 401 만 세션 만료로 본다
      if (statusRef.current !== AUTH_STATUS.AUTHENTICATED) return
      statusRef.current = AUTH_STATUS.ANONYMOUS
      setUser(null)
      setStatus(AUTH_STATUS.ANONYMOUS)
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

    fetchSession()
      .then((authenticated) => (authenticated ? apiFetch('/api/auth/me') : null))
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setStatus(me ? AUTH_STATUS.AUTHENTICATED : AUTH_STATUS.ANONYMOUS)
      })
      .catch(() => {
        if (cancelled) return
        setUser(null)
        setStatus(AUTH_STATUS.ANONYMOUS)
      })

    return () => {
      cancelled = true
    }
  }, [])

  /** 로그인 */
  const login = useCallback(async (username, password) => {
    try {
      const me = await apiFetch('/api/auth/login', {
        method: 'POST',
        body: { username, password },
      })
      clearCsrfToken()
      setUser(me)
      setStatus(AUTH_STATUS.AUTHENTICATED)
      return me
    } catch (error) {
      setUser(null)
      setStatus(AUTH_STATUS.ANONYMOUS)
      throw error
    }
  }, [])

  /** 로그아웃 */
  const logout = useCallback(async () => {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' })
    } finally {
      clearCsrfToken()
      setUser(null)
      setStatus(AUTH_STATUS.ANONYMOUS)
    }
  }, [])

  const value = useMemo(
    () => ({ user, status, isLoggedIn: status === AUTH_STATUS.AUTHENTICATED, login, logout }),
    [user, status, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** 로그인 상태와 login·logout */
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.')
  return context
}
