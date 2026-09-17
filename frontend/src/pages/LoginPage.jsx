import { Link, useSearchParams } from 'react-router-dom'
import { NAVER_LOGIN_URL } from '../config/api.js'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon, NaverIcon } from '../components/icons.jsx'
import './LoginPage.css'

// 로그인 실패/세션 만료 안내 (SPECIFICATION.md 2.1)
const ERROR_MESSAGES = {
  access_denied: '네이버 로그인 동의가 취소되었습니다. 다시 시도해 주세요.',
  session_expired: '세션이 만료되었습니다. 다시 로그인해 주세요.',
}

export default function LoginPage() {
  const { themeKey, changeTheme } = useTheme()
  const [searchParams] = useSearchParams()

  const errorKey = searchParams.get('error')
  const errorMessage = errorKey
    ? (ERROR_MESSAGES[errorKey] ?? '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.')
    : null

  // OAuth 인증 화면으로는 브라우저 전체가 이동해야 하므로 XHR 이 아닌 location 이동을 쓴다.
  const handleNaverLogin = () => {
    window.location.href = NAVER_LOGIN_URL
  }

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title login-home-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
        </div>
      </header>

      <main className="login-page">
        <div className="login-panel">
          <h1 className="login-title">로그인</h1>
          <p className="login-desc">
            여행 지도는 네이버 계정으로만 로그인할 수 있어요.
            <br />
            로그인하면 여행지를 등록하고 리뷰를 남길 수 있습니다.
          </p>

          {errorMessage && (
            <p className="login-error" role="alert">
              {errorMessage}
            </p>
          )}

          <button type="button" className="naver-login-btn" onClick={handleNaverLogin}>
            <NaverIcon />
            네이버로 로그인
          </button>

          <p className="login-note">
            로그인 없이도 등록된 여행지를 둘러볼 수 있어요.
          </p>
          <Link to="/records?scope=public" className="login-browse-link">
            <ArrowLeftIcon /> 여행지 둘러보기
          </Link>
        </div>
      </main>
    </>
  )
}
