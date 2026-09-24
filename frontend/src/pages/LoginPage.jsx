import { useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
// 네이버 OAuth 복구 시 아래 import 를 되살린다 (명세 §2.1).
// import { NAVER_LOGIN_URL } from '../config/api.js'
import { useAuth } from '../context/AuthContext.jsx'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon } from '../components/icons.jsx'
import './LoginPage.css'

// 로그인 실패/세션 만료 안내 (SPECIFICATION.md 2.1)
const ERROR_MESSAGES = {
  access_denied: '네이버 로그인 동의가 취소되었습니다. 다시 시도해 주세요.',
  session_expired: '세션이 만료되었습니다. 다시 로그인해 주세요.',
}

export default function LoginPage() {
  const [searchParams] = useSearchParams()
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const errorKey = searchParams.get('error')
  const queryErrorMessage = errorKey
    ? (ERROR_MESSAGES[errorKey] ?? '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.')
    : null
  const errorMessage = formError ?? queryErrorMessage

  // 공유받은 여행 링크를 타고 들어온 경우처럼, 보던 곳으로 돌려보낸다 (명세 §2.1).
  const redirectTo = location.state?.from ?? '/trips'

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (submitting) return

    setFormError(null)
    setSubmitting(true)
    try {
      await login(username.trim(), password)
      navigate(redirectTo, { replace: true })
    } catch (error) {
      setFormError(error.message)
      setSubmitting(false)
    }
  }

  // 네이버 OAuth 복구 시 되살린다. 인증 화면으로는 브라우저 전체가 이동해야 하므로
  // XHR 이 아닌 location 이동을 쓴다 (명세 §2.1).
  // const handleNaverLogin = () => {
  //   window.location.href = NAVER_LOGIN_URL
  // }

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title login-home-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector />
        </div>
      </header>

      <main className="login-page">
        <div className="login-panel">
          <h1 className="login-title">로그인</h1>
          <p className="login-desc">
            네이버 로그인을 준비하는 동안 임시 계정으로 로그인할 수 있어요.
            <br />
            로그인하면 여행지를 등록하고 리뷰를 남길 수 있습니다.
          </p>

          {errorMessage && (
            <p className="login-error" role="alert">
              {errorMessage}
            </p>
          )}

          <form className="login-form" onSubmit={handleSubmit}>
            <label className="login-field">
              <span className="login-field-label">아이디</span>
              <input
                type="text"
                name="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
              />
            </label>

            <label className="login-field">
              <span className="login-field-label">비밀번호</span>
              <input
                type="password"
                name="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </label>

            <button type="submit" className="login-submit-btn" disabled={submitting}>
              {submitting ? '로그인 중…' : '로그인'}
            </button>
          </form>

          {/* 임시 계정이므로 안내를 숨기지 않는다. 네이버 로그인 복구 시 함께 걷어낸다. */}
          <p className="login-hint">
            임시 계정: <code>user1</code> / <code>user2</code> / <code>user3</code>, 비밀번호는 모두{' '}
            <code>password</code>
          </p>

          {/* 네이버 OAuth 복구 시 주석을 푼다 (명세 §2.1). NaverIcon 도 함께 import 한다.
          <button type="button" className="naver-login-btn" onClick={handleNaverLogin}>
            <NaverIcon />
            네이버로 로그인
          </button>
          */}

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
