import { Link } from 'react-router-dom'
import { fetchLoginHistory } from '../api/auth.js'
import usePagedList from '../hooks/usePagedList.js'
import { LOGIN_HISTORY_NOTICE } from '../data/loginHistory.js'
import AppHeader from '../components/AppHeader.jsx'
import LoginHistoryList from '../components/LoginHistoryList.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import '../styles/page.css'
import './LoginHistoryPage.css'

/** 내 로그인 이력 전체 */
export default function LoginHistoryPage() {
  const loginHistory = usePagedList(fetchLoginHistory)

  return (
    <>
      <AppHeader />

      <main className="page">
        <div className="page-inner">
          <Link to="/me" className="back-link">
            <ArrowLeftIcon /> 내 정보로
          </Link>

          <h1 className="page-title">로그인 이력</h1>
          <p className="login-history-notice">{LOGIN_HISTORY_NOTICE}</p>

          <PagedListBody
            list={loginHistory}
            loadingText="불러오는 중이에요…"
            errorText="로그인 기록을 불러오지 못했어요."
            emptyText="로그인 기록이 없어요."
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
