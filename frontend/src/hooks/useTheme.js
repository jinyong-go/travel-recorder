import { useCallback, useEffect, useState } from 'react'
import { applyTheme, getInitialTheme, storeTheme } from '../theme/themes.js'

// 라이트/다크 테마 상태를 관리하는 훅. 테마 토글이 필요한 모든 화면에서 재사용한다.
export default function useTheme() {
  const [themeKey, setThemeKey] = useState(getInitialTheme)

  useEffect(() => {
    applyTheme(themeKey)
  }, [themeKey])

  const changeTheme = useCallback((key) => {
    setThemeKey(key)
    storeTheme(key)
  }, [])

  return { themeKey, changeTheme }
}
