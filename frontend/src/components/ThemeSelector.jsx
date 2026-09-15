import { THEMES } from '../theme/themes.js'
import { MoonIcon, SunIcon } from './icons.jsx'

// 라이트 / 다크 두 가지 모드를 즉시 전환하는 토글 버튼
export default function ThemeSelector({ themeKey, onChange }) {
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
