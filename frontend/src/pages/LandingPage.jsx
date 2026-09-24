import { Link } from 'react-router-dom'
import { CATEGORIES, categoryIcon } from '../data/records.js'
import { useRecords } from '../context/RecordsContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import HeaderAuth from '../components/HeaderAuth.jsx'
import { MapPinIcon, MapViewIcon, PlusIcon } from '../components/icons.jsx'
import './LandingPage.css'

const FEATURES = [
  {
    key: 'record',
    icon: <PlusIcon />,
    title: '다녀온 곳을 기록',
    desc: '장소·카테고리·별점·사진·코멘트를 한 번에 남겨 두세요.',
  },
  {
    key: 'browse',
    icon: <MapViewIcon />,
    title: '카테고리로 탐색',
    desc: '관광지·쇼핑·맛집으로 걸러 보고 별점순·거리순으로 정렬합니다.',
  },
  {
    key: 'share',
    icon: <MapPinIcon width="18" height="18" />,
    title: '지도로 확인',
    desc: '카드에서 바로 지도를 열어 위치를 확인할 수 있어요.',
  },
]

const SELECTABLE_CATEGORIES = CATEGORIES.filter((c) => c.key !== 'all')

export default function LandingPage() {
  const { themeKey, changeTheme } = useTheme()
  const { listTripsByScope } = useRecords()
  const { status } = useAuth()
  // 랜딩은 비로그인도 보는 화면이므로 전체 공개 여행만 센다.
  const publicTrips = listTripsByScope('public')

  return (
    <>
      <header className="app-header">
        <h1 className="app-title">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </h1>
        <div className="header-actions">
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
          <HeaderAuth />
        </div>
      </header>

      <main className="landing-page">
        <section className="landing-hero">
          <p className="landing-eyebrow">여행 기록 아카이브</p>
          <h2 className="landing-headline">
            다녀온 여행지를
            <br />
            지도처럼 모아 두세요
          </h2>
          <p className="landing-sub">
            여행지를 등록해, 카테고리와 별점으로 정리된 나만의 여행 기록을 만들어 보세요.
          </p>

          <div className="landing-cta">
            <Link to="/trips?scope=public" className="landing-cta-primary">
              여행지 둘러보기
            </Link>
            {/* 네이버 로그인 복구 시 "네이버로 시작하기" 로 되돌린다 (명세 §2.1).
                이미 로그인했으면 보이지 않는다. 확인 중에도 그리지 않는다 — 먼저 그리면
                로그인한 사용자에게 한 번 깜빡인다 (헤더 로그인 링크와 같은 이유). */}
            {status === 'anonymous' && (
              <Link to="/login" className="landing-cta-secondary">
                로그인하고 시작하기
              </Link>
            )}
          </div>

          <p className="landing-stat">
            지금까지 <strong>{publicTrips.length}</strong>건의 여행이 공개되어 있어요.
          </p>

          <ul className="landing-category-chips">
            {SELECTABLE_CATEGORIES.map((c) => (
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
