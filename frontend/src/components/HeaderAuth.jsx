import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/**
 * 헤더 우상단의 인증 영역 (명세 §2.1).
 * 비로그인이면 로그인 링크를, 로그인 상태면 프로필과 로그아웃 버튼을 보여준다.
 *
 * 세 화면의 헤더가 같은 것을 그리므로 한 곳에 둔다.
 */
export default function HeaderAuth() {
  const { user, status, logout } = useAuth()

  // 세션 확인 전에는 아무것도 그리지 않는다. 로그인 링크를 먼저 보여 주면
  // 로그인한 사용자에게 화면이 한 번 깜빡인다.
  if (status === 'loading') return null

  if (status !== 'authenticated') {
    return (
      <Link to="/login" className="header-login-link">
        로그인
      </Link>
    )
  }

  return (
    <div className="header-user">
      {user.profileImageUrl ? (
        <img className="header-user-avatar" src={user.profileImageUrl} alt="" />
      ) : (
        <span className="header-user-avatar header-user-avatar-fallback" aria-hidden="true">
          {user.name.slice(0, 1)}
        </span>
      )}
      <span className="header-user-name">{user.name}</span>
      <button type="button" className="header-logout-btn" onClick={logout}>
        로그아웃
      </button>
    </div>
  )
}
