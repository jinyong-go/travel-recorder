import { Link } from 'react-router-dom'
import { fetchLoginHistory } from '../api/auth.js'
import { useAuth } from '../context/AuthContext.jsx'
import usePagedList from '../hooks/usePagedList.js'
import { LOGIN_HISTORY_NOTICE } from '../data/loginHistory.js'
import AppHeader from '../components/AppHeader.jsx'
import LoginHistoryList from '../components/LoginHistoryList.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import UserAvatar from '../components/UserAvatar.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import '../styles/page.css'
import './MyInfoPage.css'

/** 내 정보와 최근 로그인 */
export default function MyInfoPage() {
  const { user } = useAuth()
  const loginHistory = usePagedList(fetchLoginHistory)

  return (
    <>
      <AppHeader />

      <main className="page">
        <div className="page-inner">
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          <h1 className="page-title">내 정보</h1>

          <section className="my-info-card">
            <UserAvatar user={user} size={72} />

            <dl className="my-info-fields">
              <dt>이름</dt>
              <dd>{user.name}</dd>
              <dt>이메일</dt>
              <dd>{user.email ?? '없음'}</dd>
            </dl>
          </section>

          {!user.email && <p className="my-info-note">이메일 정보가 없어 초대를 받을 수 없어요.</p>}

          <section className="my-info-section" aria-labelledby="my-info-login-history">
            <h2 id="my-info-login-history" className="my-info-section-title">
              최근 로그인
            </h2>
            <p className="my-info-note">{LOGIN_HISTORY_NOTICE}</p>
            <PagedListBody
              list={loginHistory}
              loadingText="불러오는 중이에요…"
              errorText="로그인 기록을 불러오지 못했어요."
              emptyText="로그인 기록이 없어요."
              noteClassName="my-info-note"
              errorClassName="field-error"
              showMore={false}
            >
              <LoginHistoryList items={loginHistory.items} />
            </PagedListBody>
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
