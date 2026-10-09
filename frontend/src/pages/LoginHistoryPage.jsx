import { Link } from 'react-router-dom'
import { fetchLoginHistory } from '../api/auth.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import usePagedList from '../hooks/usePagedList.js'
import { LOGIN_HISTORY_NOTICE } from '../data/loginHistory.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import LoginHistoryList from '../components/LoginHistoryList.jsx'
import { ArrowLeftIcon, MapPinIcon } from '../components/icons.jsx'
import './LoginHistoryPage.css'

/**
 * 내 로그인 이력 전체 (명세 §5.11). 최신순이며 "더 보기" 로 이어 붙인다.
 *
 * 대상은 언제나 본인이라 경로에 사용자를 담지 않는다. 계정 메뉴에는 두지 않고 내 정보의
 * "전체 보기" 로만 들어온다.
 */
export default function LoginHistoryPage() {
  const { items, status, hasNext, loadMore, reload } = usePagedList(fetchLoginHistory)

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

      <main className="login-history-page">
        <div className="login-history-inner">
          <Link to="/me" className="back-link">
            <ArrowLeftIcon /> 내 정보로
          </Link>

          <h1 className="login-history-title">로그인 이력</h1>
          <p className="login-history-notice">{LOGIN_HISTORY_NOTICE}</p>

          {status === REQUEST_STATUS.ERROR && (
            <p className="field-error">
              로그인 기록을 불러오지 못했습니다.{' '}
              <button type="button" className="link-button" onClick={reload}>
                다시 시도
              </button>
            </p>
          )}
          {status === REQUEST_STATUS.COMPLETE && items.length === 0 ? (
            <p className="login-history-notice">로그인 기록이 없습니다.</p>
          ) : (
            <LoginHistoryList items={items} />
          )}
          {status === REQUEST_STATUS.LOADING && (
            <p className="login-history-notice">불러오는 중이에요…</p>
          )}
          {hasNext && status !== REQUEST_STATUS.LOADING && (
            <button type="button" className="link-button" onClick={loadMore}>
              더 보기
            </button>
          )}
        </div>
      </main>
    </>
  )
}
