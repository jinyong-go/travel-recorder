import { Link } from 'react-router-dom'
import { fetchLoginHistory } from '../api/auth.js'
import usePagedList from '../hooks/usePagedList.js'
import { LOGIN_HISTORY_NOTICE } from '../data/loginHistory.js'
import AppHeader from '../components/AppHeader.jsx'
import LoginHistoryList from '../components/LoginHistoryList.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import './LoginHistoryPage.css'

/**
 * 내 로그인 이력 전체. 최신순이며 "더 보기" 로 이어 붙인다.
 *
 * 대상은 언제나 본인이라 경로에 사용자를 담지 않는다. 계정 메뉴에는 두지 않고 내 정보의
 * "전체 보기" 로만 들어온다.
 */
export default function LoginHistoryPage() {
  const loginHistory = usePagedList(fetchLoginHistory)

  return (
    <>
      <AppHeader />

      <main className="login-history-page">
        <div className="login-history-inner">
          <Link to="/me" className="back-link">
            <ArrowLeftIcon /> 내 정보로
          </Link>

          <h1 className="login-history-title">로그인 이력</h1>
          <p className="login-history-notice">{LOGIN_HISTORY_NOTICE}</p>

          <PagedListBody
            list={loginHistory}
            loadingText="불러오는 중이에요…"
            errorText="로그인 기록을 불러오지 못했습니다."
            emptyText="로그인 기록이 없습니다."
            noteClassName="login-history-notice"
            errorClassName="field-error"
          >
            <LoginHistoryList items={loginHistory.items} />
          </PagedListBody>
        </div>
      </main>
    </>
  )
}
