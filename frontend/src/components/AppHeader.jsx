import { Link } from 'react-router-dom'
import ThemeSelector from './ThemeSelector.jsx'
import HeaderAuth from './HeaderAuth.jsx'
import AppLogoMark from './AppLogoMark.jsx'

/**
 * 화면 위의 공통 헤더 — 홈으로 가는 로고, 테마 토글, 인증 영역.
 *
 * `children` 은 테마 토글 앞에 놓는 화면 전용 동작이다 (여행 목록의 설정 메뉴). `receivedCount` 는
 * `HeaderAuth` 에 그대로 넘긴다 — 받은 초대 수를 이미 읽은 화면이 넘겨 같은 조회를 막는다.
 *
 * 랜딩(로고가 곧 페이지 제목)과 로그인(인증 영역이 없다)은 모양이 달라 이 헤더를 쓰지 않는다.
 */
export default function AppHeader({ receivedCount, children }) {
  return (
    <header className="app-header">
      <Link to="/" className="app-title app-title-link">
        <AppLogoMark />
      </Link>
      <div className="header-actions">
        {children}
        <ThemeSelector />
        <HeaderAuth receivedCount={receivedCount} />
      </div>
    </header>
  )
}
