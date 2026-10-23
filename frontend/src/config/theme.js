import { readStorage, writeStorage } from '../utils/storage.js'

/** 화면 테마와 그 저장. 라이트(파스텔 연두)와 다크(검은 바탕에 연두 포인트) 두 가지만 둔다. */
export const THEMES = [
  { key: 'light', label: '라이트', swatch: '#7bc76a' },
  { key: 'dark', label: '다크', swatch: '#0f0f0f' },
]

const STORAGE_KEY = 'travel-recorder:theme'

const normalize = (key) => (key === 'dark' ? 'dark' : 'light')

/** 저장된 테마. 예전 다중 팔레트(green/sky/lavender/peach) 저장값은 라이트로 취급한다. */
export const getStoredTheme = () => {
  const stored = readStorage(STORAGE_KEY)
  return stored ? normalize(stored) : null
}

export const storeTheme = (key) => writeStorage(STORAGE_KEY, normalize(key))

/** 저장된 선택이 있으면 우선하고, 없으면 시스템 설정(prefers-color-scheme)을 따른다. */
export const getInitialTheme = () => {
  const stored = getStoredTheme()
  if (stored) return stored
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

/** 문서에 테마를 적용한다. 다크만 속성을 달고, 라이트는 기본 변수를 쓴다. */
export const applyTheme = (key) => {
  if (normalize(key) === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
  } else {
    document.documentElement.removeAttribute('data-theme')
  }
}
