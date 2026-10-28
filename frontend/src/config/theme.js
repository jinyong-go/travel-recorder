import { readStorage, writeStorage } from '../utils/storage.js'

/** 화면 테마(라이트·다크) */
export const THEMES = [
  { key: 'light', label: '라이트', swatch: '#7bc76a' },
  { key: 'dark', label: '다크', swatch: '#0f0f0f' },
]

const STORAGE_KEY = 'travel-recorder:theme'

const normalize = (key) => (key === 'dark' ? 'dark' : 'light')

/** 저장된 테마 */
export const getStoredTheme = () => {
  const stored = readStorage(STORAGE_KEY)
  return stored ? normalize(stored) : null
}

export const storeTheme = (key) => writeStorage(STORAGE_KEY, normalize(key))

/** 처음 적용할 테마 */
export const getInitialTheme = () => {
  const stored = getStoredTheme()
  if (stored) return stored
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

/** 문서에 테마를 적용한다 */
export const applyTheme = (key) => {
  if (normalize(key) === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
  } else {
    document.documentElement.removeAttribute('data-theme')
  }
}
