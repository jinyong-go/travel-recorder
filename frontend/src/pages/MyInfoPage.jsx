import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import { ArrowLeftIcon, MapPinIcon } from '../components/icons.jsx'
import './MyInfoPage.css'

/**
 * 로그인 사용자 본인 정보 (명세 §5.10). 읽기 전용이다.
 *
 * 부팅 시 받아 둔 본인 정보를 그대로 쓰고 따로 조회하지 않는다. `RequireAuth` 안에서만
 * 그려지므로 `user` 는 항상 있다.
 */
export default function MyInfoPage() {
  const { user } = useAuth()
  const { themeKey, changeTheme } = useTheme()

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
          <HeaderAuth />
        </div>
      </header>

      <main className="my-info-page">
        <div className="my-info-inner">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          <h1 className="my-info-title">내 정보</h1>

          <section className="my-info-card">
            {user.profileImageUrl ? (
              <img className="my-info-avatar" src={user.profileImageUrl} alt="" />
            ) : (
              <span className="my-info-avatar my-info-avatar-fallback" aria-hidden="true">
                {user.name.slice(0, 1)}
              </span>
            )}

            <dl className="my-info-fields">
              <dt>이름</dt>
              <dd>{user.name}</dd>
              <dt>이메일</dt>
              <dd>{user.email ?? '없음'}</dd>
            </dl>
          </section>

          {/* 초대는 이메일 정확 일치로 상대를 찾으므로, 이메일이 없으면 초대를 받을 수 없다 (공통 명세 §3.7). */}
          <p className="my-info-note">
            {user.email
              ? '그룹 소유자가 이 이메일로 초대를 보내요.'
              : '이메일 정보가 없어 초대를 받을 수 없어요.'}
          </p>
        </div>
      </main>
    </>
  )
}
