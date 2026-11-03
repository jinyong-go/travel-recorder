import { useCallback, useEffect, useRef } from 'react'

/** 늦게 도착한 이전 응답을 걸러 낸다 */
export default function useLatestRequest() {
  const latest = useRef(0)

  useEffect(
    () => () => {
      latest.current += 1
    },
    [],
  )

  return useCallback(() => {
    const id = ++latest.current
    return () => id === latest.current
  }, [])
}
