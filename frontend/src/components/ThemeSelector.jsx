import { useState } from 'react'
import { THEMES, applyTheme, getInitialTheme, storeTheme } from '../theme/themes.js'
import { MoonIcon, SunIcon } from './icons.jsx'

/**
 * 라이트 / 다크 두 가지 모드를 즉시 전환하는 토글 버튼.
 *
 * 테마 상태는 이 버튼만 바꾸므로 여기서 들고 있다. 저장된 테마를 문서에 처음 적용하는 일은
 * `main.jsx` 가 렌더링 전에 한다 — 헤더가 없는 화면(여행 만들기, 404)으로 바로 들어와도
 * 테마가 맞아야 하기 때문이다.
 */
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
