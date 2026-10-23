import { Link } from 'react-router-dom'
import { fetchLoginHistory } from '../api/auth.js'
import { useAuth } from '../context/AuthContext.jsx'
import usePagedList from '../hooks/usePagedList.js'
import { LOGIN_HISTORY_NOTICE } from '../data/loginHistory.js'
import AppHeader from '../components/AppHeader.jsx'
import LoginHistoryList from '../components/LoginHistoryList.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import './MyInfoPage.css'

/**
 * 로그인 사용자 본인 정보. 읽기 전용이다.
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
      <AppHeader />

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

          {/* 초대는 이메일 정확 일치로 상대를 찾으므로, 이메일이 없으면 초대를 받을 수 없다. */}
          {!user.email && <p className="my-info-note">이메일 정보가 없어 초대를 받을 수 없어요.</p>}

          <section className="my-info-section" aria-labelledby="my-info-login-history">
            <h2 id="my-info-login-history" className="my-info-section-title">
              최근 로그인
            </h2>
            <p className="my-info-note">{LOGIN_HISTORY_NOTICE}</p>
            {/* 실패해도 위의 프로필은 그대로 둔다. 이 섹션 자리에만 알린다. 첫 페이지만 보여 주므로
                "더 보기" 대신 전체 보기 화면으로 보낸다. 이력은 기능 도입 시점부터 쌓이므로
                로그인한 사용자에게도 비어 있을 수 있다. */}
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
