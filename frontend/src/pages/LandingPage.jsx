import { Link } from 'react-router-dom'
import { CATEGORIES, categoryIcon } from '../data/records.js'
import { fetchTrips } from '../api/trips.js'
import useResource from '../hooks/useResource.js'
import { useAuth } from '../context/AuthContext.jsx'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import AppLogoMark from '../components/AppLogoMark.jsx'
import { MapViewIcon, PlusIcon, UsersIcon } from '../components/icons.jsx'
import './LandingPage.css'
import { AUTH_STATUS } from '../context/authStatus.js'

const FEATURES = [
  {
    key: 'record',
    icon: <PlusIcon />,
    title: '여행 단위로 기록',
    desc: '여행을 만들고 다녀온 곳마다 평점·사진·메모를 남겨 두세요.',
  },
  {
    key: 'share',
    icon: <UsersIcon />,
    title: '공개 범위를 골라 공유',
    desc: '여행마다 나만 보기·그룹 공유·전체 공개 중에서 고를 수 있어요.',
  },
  {
    key: 'map',
    icon: <MapViewIcon />,
    title: '지도로 확인',
    desc: '여행지마다 지도를 열어 위치를 확인할 수 있어요.',
  },
]

/** 공개 여행 첫 페이지. 건수(`totalElements`)만 쓴다. */
const loadPublicTrips = () => fetchTrips('public', 'recent', 0)

/** 첫 화면. 서비스 소개와 공개 여행 건수를 보여 준다. 비로그인도 보는 화면이다. */
export default function LandingPage() {
  const { status } = useAuth()
  // 랜딩은 비로그인도 보는 화면이므로 전체 공개 여행만 센다. 건수만 필요해 첫 페이지의
  // totalElements 를 쓴다. 받기 전이나 실패했을 때는 문장을 그리지 않는다 — 0건으로 보이면 거짓이다.
  const publicTripCount = useResource(loadPublicTrips).data?.totalElements ?? null

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
          <p className="landing-eyebrow">여행 기록 아카이브</p>
          <h2 className="landing-headline">
            다녀온 여행을
            <br />
            지도처럼 모아 두세요
          </h2>
          <p className="landing-sub">
            여행을 만들고 다녀온 곳을 하나씩 남겨, 나만 보거나 원하는 사람과 나눠 보세요.
          </p>

          <div className="landing-cta">
            <Link to="/trips?scope=public" className="landing-cta-primary">
              여행 둘러보기
            </Link>
            {/* 네이버 로그인 복구 시 "네이버로 시작하기" 로 되돌린다.
                이미 로그인했으면 보이지 않는다. 확인 중에도 그리지 않는다 — 먼저 그리면
                로그인한 사용자에게 한 번 깜빡인다 (헤더 로그인 링크와 같은 이유). */}
            {status === AUTH_STATUS.ANONYMOUS && (
              <Link to="/login" className="landing-cta-secondary">
                로그인하고 시작하기
              </Link>
            )}
          </div>

          {publicTripCount != null && (
            <p className="landing-stat">
              지금까지 <strong>{publicTripCount}</strong>건의 여행이 공개되어 있어요.
            </p>
          )}

          <ul className="landing-category-chips">
            {CATEGORIES.map((c) => (
              <li key={c.key} className="landing-category-chip">
                <span aria-hidden="true">{categoryIcon(c.key)}</span> {c.label}
              </li>
            ))}
          </ul>
        </section>

        <section className="landing-features" aria-label="주요 기능">
          {FEATURES.map((f) => (
            <article key={f.key} className="landing-feature-card">
              <span className="landing-feature-icon">{f.icon}</span>
              <h3 className="landing-feature-title">{f.title}</h3>
              <p className="landing-feature-desc">{f.desc}</p>
            </article>
          ))}
        </section>
      </main>
    </>
  )
}
