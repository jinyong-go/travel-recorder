export const THEMES = [
  { key: 'green', label: '연두', swatch: '#7bc76a' },
  { key: 'sky', label: '하늘', swatch: '#6fbde0' },
  { key: 'lavender', label: '라벤더', swatch: '#a98be0' },
  { key: 'peach', label: '피치', swatch: '#ef9d6f' },
  { key: 'dark', label: '다크', swatch: '#2b342b' },
]

const STORAGE_KEY = 'travel-recorder:theme'

export const getStoredTheme = () => {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export const storeTheme = (key) => {
  try {
    localStorage.setItem(STORAGE_KEY, key)
  } catch {
    // 저장 공간을 사용할 수 없는 환경은 무시
  }
}

export const applyTheme = (key) => {
  if (key === 'green') {
    document.documentElement.removeAttribute('data-theme')
  } else {
    document.documentElement.setAttribute('data-theme', key)
  }
}
