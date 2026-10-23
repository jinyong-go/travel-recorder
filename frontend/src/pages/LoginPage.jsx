import { useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import ThemeSelector from '../components/ThemeSelector.jsx'
import AppLogoMark from '../components/AppLogoMark.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import './LoginPage.css'

const LOGIN_FAILED = '로그인에 실패했어요. 잠시 후 다시 시도해 주세요.'

/** 주소의 `?error=` 로 넘어오는 안내. 세션 만료는 AuthContext 가 이 화면으로 보낼 때 붙인다. */
const QUERY_ERROR = {
  access_denied: '네이버 로그인 동의가 취소되었어요. 다시 시도해 주세요.',
  session_expired: '세션이 만료되었어요. 다시 로그인해 주세요.',
}

const LOGIN_ERROR = {
  UNAUTHENTICATED: '아이디 또는 비밀번호가 올바르지 않아요.',
}

/**
 * 로그인 화면. 지금은 임시 아이디/비밀번호 로그인이며, 성공하면 들어오기 전에 보던 곳으로 돌려보낸다.
 */
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
    ? (QUERY_ERROR[errorKey] ?? LOGIN_FAILED)
    : null
  const shownError = formError ?? queryErrorMessage

  // 공유받은 여행 링크를 타고 들어온 경우처럼, 보던 곳으로 돌려보낸다.
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
      setFormError(errorMessage(error, LOGIN_ERROR, LOGIN_FAILED))
      setSubmitting(false)
    }
  }

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title login-home-link">
          <AppLogoMark />
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
            로그인하면 여행을 만들고 다녀온 곳을 기록할 수 있어요.
          </p>

          {shownError && (
            <p className="login-error" role="alert">
              {shownError}
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

          <p className="login-note">
            로그인 없이도 공개된 여행을 둘러볼 수 있어요.
          </p>
          <Link to="/trips?scope=public" className="login-browse-link">
            <ArrowLeftIcon /> 여행 둘러보기
          </Link>
        </div>
      </main>
    </>
  )
}
