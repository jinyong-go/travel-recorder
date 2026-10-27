import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import useMapMode from '../hooks/useMapMode.js'
import SettingsMenu from './SettingsMenu.jsx'
import ThemeSelector from './ThemeSelector.jsx'
import HeaderAuth from './HeaderAuth.jsx'
import AppLogoMark from './AppLogoMark.jsx'

/**
 * 화면 위의 공통 헤더 — 홈으로 가는 로고, 여행 목록 링크(로그인 시), 설정 메뉴, 테마 토글, 인증 영역.
 *
 * `receivedCount` 는 `HeaderAuth` 에 그대로 넘긴다 — 받은 초대 수를 이미 읽은 화면이 넘겨 같은 조회를 막는다.
 *
 * `mapMode` 는 지도를 그리는 화면(여행·기록 상세)이 자기 `useMapMode()` 결과를 넘긴다. 헤더가 따로
 * 들고 있으면 설정을 바꿔도 그 화면의 지도 방식은 그대로다. 넘기지 않으면 헤더가 직접 들고, 바꾼 값은
 * 저장되어 다음 화면이 읽는다.
 *
 * 랜딩(로고가 곧 페이지 제목)과 로그인(인증 영역이 없다)은 모양이 달라 이 헤더를 쓰지 않는다.
 */
export default function AppHeader({ receivedCount, mapMode: givenMapMode }) {
  const { isLoggedIn } = useAuth()
  // 훅은 조건 없이 부른다. 넘겨받은 값이 있으면 그쪽을 쓴다.
  const ownMapMode = useMapMode()
  const { mapMode, changeMapMode } = givenMapMode ?? ownMapMode

  return (
    <header className="app-header">
      <Link to="/" className="app-title app-title-link">
        <AppLogoMark />
      </Link>
      <div className="header-actions">
        {/* 목록 자체에서는 aria-current 로 지금 화면임을 알린다. 상세(/trips/:id)까지 켜지지 않게 end 를 둔다. */}
        {isLoggedIn && (
          <NavLink to="/trips" end className="header-nav-link">
            여행 목록
          </NavLink>
        )}
        <SettingsMenu mapMode={mapMode} onChangeMapMode={changeMapMode} />
        <ThemeSelector />
        <HeaderAuth receivedCount={receivedCount} />
      </div>
    </header>
  )
}
