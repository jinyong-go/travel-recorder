import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as api from '../api/groups.js'
import { useAuth } from './AuthContext.jsx'

const GroupsContext = createContext(null)

/**
 * 그룹과 초대의 서버 상태.
 *
 * `RecordsContext` 와 나누어 둔 것은 갱신 방식이 다르기 때문이다. 저쪽은 목업을 들고 있는
 * 클라이언트 상태라 즉시 바뀌지만, 이쪽은 서버가 진실이라 쓰기 뒤에 다시 읽어야 한다.
 *
 * **낙관적 갱신을 하지 않는다.** 정원 판정이 서버에 있어(공통 명세 §3.7) 수락이 성공할지
 * 클라이언트가 미리 알 수 없고, 먼저 그려 두었다가 되돌리면 그 사이 화면이 거짓말을 한다.
 */
export function GroupsProvider({ children }) {
  const { isLoggedIn } = useAuth()

  const [groups, setGroups] = useState([])
  // 'loading' | 'ready' | 'error' — 비로그인은 조회 자체를 하지 않으므로 'ready' 에 빈 목록이다.
  const [status, setStatus] = useState('loading')

  /** 받은 초대 건수. 그룹 목록의 알림 줄과 헤더 계정 메뉴가 이 값만 쓴다 (명세 §5.8.1). */
  const [receivedCount, setReceivedCount] = useState(0)

  const reloadGroups = useCallback(async () => {
    if (!isLoggedIn) {
      setGroups([])
      setReceivedCount(0)
      setStatus('ready')
      return
    }
    setStatus('loading')
    try {
      const [list, received] = await Promise.all([api.fetchGroups(), api.fetchReceivedInvites(0)])
      setGroups(list)
      setReceivedCount(received.totalElements)
      setStatus('ready')
    } catch {
      // 사유를 나누지 않는다. 목록을 못 받았다는 사실만 화면에 필요하다.
      setStatus('error')
    }
  }, [isLoggedIn])

  useEffect(() => {
    reloadGroups()
  }, [reloadGroups])

  const value = useMemo(
    () => ({ groups, status, receivedCount, reloadGroups, setReceivedCount }),
    [groups, status, receivedCount, reloadGroups],
  )

  return <GroupsContext.Provider value={value}>{children}</GroupsContext.Provider>
}

export function useGroups() {
  const context = useContext(GroupsContext)
  if (!context) throw new Error('useGroups 는 GroupsProvider 안에서만 쓸 수 있습니다.')
  return context
}
