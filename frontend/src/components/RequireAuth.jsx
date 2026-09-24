import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * 로그인이 필요한 라우트를 묶는 레이아웃 라우트 (명세 §2.1).
 * 확인 중에는 아무것도 그리지 않고, 비로그인이면 보던 경로를 들고 로그인 화면으로 보낸다.
 *
 * 화면을 가릴 뿐 보안 경계가 아니다. 실제 차단은 서버가 한다 (명세 §2.3).
 */
export default function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  // 확인 전에 보내면 로그인한 사용자도 새로고침할 때마다 로그인 화면으로 튕긴다.
  if (status === 'loading') return null
  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}
