import { useState } from 'react'
import { THEMES, applyTheme, getInitialTheme, storeTheme } from '../config/theme.js'
import { MoonIcon, SunIcon } from './icons.jsx'
import './ThemeSelector.css'

/** 라이트·다크 테마 토글 */
export default function ThemeSelector() {
  const [themeKey, setThemeKey] = useState(getInitialTheme)

  const onChange = (key) => {
    setThemeKey(key)
    storeTheme(key)
    applyTheme(key)
  }

  const isDark = themeKey === 'dark'
  const next = isDark ? 'light' : 'dark'
  const nextLabel = THEMES.find((t) => t.key === next)?.label ?? next

  return (
    <button
      type="button"
      className="theme-selector-btn"
      onClick={() => onChange(next)}
      aria-label={`${nextLabel} 모드로 전환`}
      title={`${nextLabel} 모드로 전환`}
    >
      {isDark ? <MoonIcon /> : <SunIcon />}
      <span className="theme-selector-label">{isDark ? '다크' : '라이트'}</span>
    </button>
  )
}
