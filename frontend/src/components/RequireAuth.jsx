import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { AUTH_STATUS } from '../context/authStatus.js'

/** 로그인이 필요한 화면을 감싸고, 비로그인이면 로그인 화면으로 보낸다 */
export default function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  // 로그인 확인 전에는 그리지 않는다
  if (status === AUTH_STATUS.LOADING) return null
  if (status !== AUTH_STATUS.AUTHENTICATED) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}
