// 테마는 라이트 / 다크 2가지 모드만 제공한다.
// 라이트: 기존 파스텔 연두 기본 테마, 다크: 검은색 기반 + 연두 포인트(테두리/액센트)
export const THEMES = [
  { key: 'light', label: '라이트', swatch: '#7bc76a' },
  { key: 'dark', label: '다크', swatch: '#0f0f0f' },
]

const STORAGE_KEY = 'travel-recorder:theme'

const normalize = (key) => (key === 'dark' ? 'dark' : 'light')

export const getStoredTheme = () => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    // 기존 다중 팔레트(green/sky/lavender/peach) 저장값은 라이트로 취급
    return stored ? normalize(stored) : null
  } catch {
    return null
  }
}

export const storeTheme = (key) => {
  try {
    localStorage.setItem(STORAGE_KEY, normalize(key))
  } catch {
    // 저장 공간을 사용할 수 없는 환경은 무시
  }
}

// 저장된 선택이 있으면 우선하고, 없으면 시스템 설정(prefers-color-scheme)을 따른다.
export const getInitialTheme = () => {
  const stored = getStoredTheme()
  if (stored) return stored
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

export const applyTheme = (key) => {
  if (normalize(key) === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
  } else {
    document.documentElement.removeAttribute('data-theme')
  }
}
