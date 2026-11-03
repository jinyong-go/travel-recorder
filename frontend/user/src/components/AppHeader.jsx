import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import useMapMode from '../hooks/useMapMode.js'
import SettingsMenu from './SettingsMenu.jsx'
import ThemeSelector from './ThemeSelector.jsx'
import HeaderAuth from './HeaderAuth.jsx'
import AppLogoMark from './AppLogoMark.jsx'

/** 화면 위의 공통 헤더 */
export default function AppHeader({ receivedCount, mapMode: givenMapMode }) {
  const { isLoggedIn } = useAuth()
  const ownMapMode = useMapMode()
  const { mapMode, changeMapMode } = givenMapMode ?? ownMapMode

  return (
    <header className="app-header">
      <Link to="/" className="app-title app-title-link">
        <AppLogoMark />
      </Link>
      <div className="header-actions">
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
