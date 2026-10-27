import { Link } from 'react-router-dom'
import { tripPeriodLabel } from '../data/trips.js'
import { fetchTrips } from '../api/trips.js'
import useResource from '../hooks/useResource.js'
import { useAuth } from '../context/AuthContext.jsx'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import AppLogoMark from '../components/AppLogoMark.jsx'
import TripCover from '../components/TripCover.jsx'
import './LandingPage.css'
import { AUTH_STATUS } from '../context/authStatus.js'

/** 공개 여행 첫 페이지(최근 등록순). 앞의 몇 건과 전체 건수를 쓴다. */
const loadPublicTrips = () => fetchTrips('PUBLIC', 'RECENT', 0)

/** 랜딩에 보여 줄 최근 공개 여행 수. */
const RECENT_TRIP_COUNT = 4

/** 첫 화면. 기능을 설명하는 대신 최근 공개된 여행을 보여 준다. 비로그인도 보는 화면이다. */
export default function LandingPage() {
  const { status } = useAuth()
  // 랜딩은 비로그인도 보는 화면이므로 전체 공개 여행만 읽는다. 받기 전·실패·0건이면 최근 여행
  // 영역을 통째로 그리지 않는다 — 빈 자리나 "0건"은 처음 온 사람에게 비어 있는 서비스로 읽힌다.
  const publicTrips = useResource(loadPublicTrips).data
  const recentTrips = publicTrips?.content.slice(0, RECENT_TRIP_COUNT) ?? []

  return (
    <>
      <header className="app-header">
        <h1 className="app-title">
          <AppLogoMark />
        </h1>
        <div className="header-actions">
          <ThemeSelector />
          <HeaderAuth />
        </div>
      </header>

      <main className="landing-page">
        <section className="landing-hero">
          <h2 className="landing-headline">
            다녀온 여행을
            <br />
            지도처럼 모아 두세요
          </h2>
          <p className="landing-sub">
            여행을 만들고 다녀온 곳을 하나씩 남겨, 나만 보거나 원하는 사람과 나눠 보세요.
          </p>

          <div className="landing-cta">
            <Link to="/trips?scope=PUBLIC" className="btn-primary">
              여행 둘러보기
            </Link>
            {/* 네이버 로그인 복구 시 "네이버로 시작하기" 로 되돌린다.
                이미 로그인했으면 보이지 않는다. 확인 중에도 그리지 않는다 — 먼저 그리면
                로그인한 사용자에게 한 번 깜빡인다 (헤더 로그인 링크와 같은 이유). */}
            {status === AUTH_STATUS.ANONYMOUS && (
              <Link to="/login" className="btn-secondary">
                로그인하고 시작하기
              </Link>
            )}
          </div>
        </section>

        {recentTrips.length > 0 && (
          <section className="landing-recent" aria-labelledby="landing-recent-title">
            <div className="landing-recent-head">
              <h3 id="landing-recent-title" className="landing-recent-title">
                최근 공개된 여행
              </h3>
              <p className="landing-recent-count">
                지금까지 {publicTrips.totalElements}건의 여행이 공개되어 있어요.
              </p>
              <Link to="/trips?scope=PUBLIC" className="landing-recent-more">
                모두 보기
              </Link>
            </div>
            <ul className="landing-recent-grid">
              {recentTrips.map((trip) => (
                <li key={trip.id} className="landing-trip">
                  <TripCover trip={trip} />
                  <h4 className="landing-trip-name">
                    {/* 카드 전체가 눌리도록 링크를 카드 위에 덮는다 (.landing-trip-link::after) */}
                    <Link to={`/trips/${trip.id}`} className="landing-trip-link">
                      {trip.name}
                    </Link>
                  </h4>
                  <p className="landing-trip-period">{tripPeriodLabel(trip)}</p>
                </li>
              ))}
            </ul>
          </section>
        )}
      </main>
    </>
  )
}
