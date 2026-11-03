import { fetchLoginDailyStats } from '../api/stats.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import useResource from '../hooks/useResource.js'
import './DashboardPage.css'

/** "2026-11-02" 를 "11/2" 로 줄인다 */
const shortDate = (date) => {
  const [, month, day] = date.split('-')
  return `${Number(month)}/${Number(day)}`
}

/** 최근 하루 단위 로그인 합계를 보여 주는 첫 화면 */
export default function DashboardPage() {
  const stats = useResource(fetchLoginDailyStats)

  return (
    <>
      <h1 className="page-title">대시보드</h1>
      {stats.status === REQUEST_STATUS.LOADING && <p className="note">불러오는 중…</p>}
      {stats.status === REQUEST_STATUS.ERROR && (
        <p className="error-text">
          로그인 통계를 불러오지 못했어요.{' '}
          <button type="button" className="link-button" onClick={stats.reload}>
            다시 시도
          </button>
        </p>
      )}
      {stats.status === REQUEST_STATUS.COMPLETE && <LoginStats days={stats.data} />}
    </>
  )
}

function LoginStats({ days }) {
  const totalLogins = days.reduce((sum, d) => sum + d.loginCount, 0)
  const avgUsers = days.length ? days.reduce((sum, d) => sum + d.uniqueUserCount, 0) / days.length : 0
  const maxUsers = Math.max(1, ...days.map((d) => d.uniqueUserCount))

  return (
    <div className="dashboard">
      <section className="dashboard-tiles" aria-label={`최근 ${days.length}일 요약`}>
        <div className="panel stat-tile">
          <p className="stat-label">최근 {days.length}일 로그인</p>
          <p className="stat-value">{totalLogins.toLocaleString()}회</p>
        </div>
        <div className="panel stat-tile">
          <p className="stat-label">하루 평균 로그인 사용자</p>
          <p className="stat-value">{avgUsers.toFixed(1)}명</p>
        </div>
      </section>

      <section className="panel" aria-labelledby="daily-users-title">
        <h2 id="daily-users-title" className="panel-title">
          일별 로그인 사용자
        </h2>
        {/* 숫자는 아래 표가 전하므로 막대는 보조 표현으로 숨긴다. */}
        <div className="bar-chart" aria-hidden="true">
          {days.map((d) => (
            <div key={d.statDate} className="bar-slot">
              <div className="bar-track">
                <div
                  className="bar"
                  style={{ height: `${(d.uniqueUserCount / maxUsers) * 100}%` }}
                />
                <span className="bar-tooltip">
                  {d.statDate}
                  <br />
                  사용자 {d.uniqueUserCount}명 · 로그인 {d.loginCount}회
                </span>
              </div>
              <span className="bar-label">{shortDate(d.statDate)}</span>
            </div>
          ))}
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">날짜</th>
                <th scope="col" className="num">
                  로그인 사용자
                </th>
                <th scope="col" className="num">
                  로그인 횟수
                </th>
              </tr>
            </thead>
            <tbody>
              {[...days].reverse().map((d) => (
                <tr key={d.statDate}>
                  <td>{d.statDate}</td>
                  <td className="num">{d.uniqueUserCount}</td>
                  <td className="num">{d.loginCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
