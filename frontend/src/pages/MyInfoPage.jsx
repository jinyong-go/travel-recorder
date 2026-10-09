import { Link } from 'react-router-dom'
import { fetchLoginHistory } from '../api/auth.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import { useAuth } from '../context/AuthContext.jsx'
import usePagedList from '../hooks/usePagedList.js'
import { LOGIN_HISTORY_NOTICE } from '../data/loginHistory.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import LoginHistoryList from '../components/LoginHistoryList.jsx'
import { ArrowLeftIcon, MapPinIcon } from '../components/icons.jsx'
import './MyInfoPage.css'

/**
 * 로그인 사용자 본인 정보 (명세 §5.10). 읽기 전용이다.
 *
 * 부팅 시 받아 둔 본인 정보를 그대로 쓰고 따로 조회하지 않는다. `RequireAuth` 안에서만
 * 그려지므로 `user` 는 항상 있다.
 *
 * 최근 로그인 섹션만은 직접 조회한다. 본인 정보에 이력이 없고, 첫 페이지만 보여 준다.
 */
export default function MyInfoPage() {
  const { user } = useAuth()
  const loginHistory = usePagedList(fetchLoginHistory)

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector />
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
          {!user.email && <p className="my-info-note">이메일 정보가 없어 초대를 받을 수 없어요.</p>}

          <section className="my-info-section" aria-labelledby="my-info-login-history">
            <h2 id="my-info-login-history" className="my-info-section-title">
              최근 로그인
            </h2>
            <p className="my-info-note">{LOGIN_HISTORY_NOTICE}</p>
            {loginHistory.status === REQUEST_STATUS.LOADING && (
              <p className="my-info-note">불러오는 중이에요…</p>
            )}
            {/* 실패해도 위의 프로필은 그대로 둔다. 이 섹션 자리에만 알린다. */}
            {loginHistory.status === REQUEST_STATUS.ERROR && (
              <p className="field-error">
                로그인 기록을 불러오지 못했습니다.{' '}
                <button type="button" className="link-button" onClick={loginHistory.reload}>
                  다시 시도
                </button>
              </p>
            )}
            {/* 이력은 기능 도입 시점부터 쌓이므로 로그인한 사용자에게도 비어 있을 수 있다. */}
            {loginHistory.status === REQUEST_STATUS.COMPLETE &&
              (loginHistory.items.length === 0 ? (
                <p className="my-info-note">로그인 기록이 없습니다.</p>
              ) : (
                <LoginHistoryList items={loginHistory.items} />
              ))}
            {/* 한 페이지에 다 들어오면 전체 보기도 같은 목록이라 링크를 그리지 않는다. */}
            {loginHistory.hasNext && (
              <Link to="/me/login-history" className="my-info-more-link">
                전체 보기
              </Link>
            )}
          </section>
        </div>
      </main>
    </>
  )
}
