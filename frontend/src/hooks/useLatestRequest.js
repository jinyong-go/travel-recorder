import { useCallback, useEffect, useRef } from 'react'

/**
 * 늦게 도착한 이전 응답을 버리기 위한 훅.
 *
 * 요청을 시작할 때 `beginRequest()` 를 부르고, 응답이 오면 돌려받은 `isLatest()` 로 확인한다.
 * 그 사이 새 요청이 시작됐거나 컴포넌트가 사라졌으면 false 다. 화면을 빠르게 옮기거나
 * (그룹 A → B) StrictMode 가 effect 를 두 번 돌릴 때 먼저 나간 요청의 응답이 화면을 덮지 않게 한다.
 *
 * effect 안에서만 쓰는 조회는 `cancelled` 플래그로 충분하다(`useTrip`). 이 훅은 같은 조회를
 * effect 밖(다시 읽기·더 보기)에서도 부르는 곳을 위한 것이다.
 */
export default function useLatestRequest() {
  const latest = useRef(0)

  // 사라진 컴포넌트에 도착한 응답도 버린다.
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
