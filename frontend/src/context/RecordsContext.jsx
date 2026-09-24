import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { MOCK_RECORDS } from '../data/records.js'
import { useAuth } from './AuthContext.jsx'

const today = () => new Date().toISOString().slice(0, 10)

/**
 * 비로그인 사용자. `id` 가 null 이라 어떤 작성자 판정에도 걸리지 않으므로,
 * 작성자 전용 UI 는 자연히 사라진다 (공통 명세 §3.5).
 */
const ANONYMOUS_USER = { id: null, name: '', email: null, profileImageUrl: null }

const RecordsContext = createContext(null)

/**
 * 기록의 클라이언트 상태. 백엔드 연동 전까지 목업 데이터로 동작한다 (명세 §10.2).
 *
 * **여행과 그룹은 여기 없다.** 여행은 화면이 `api/trips.js` 로 직접 읽고, 그룹과 초대는
 * `GroupsContext` 가 맡는다. 목업 기록은 `tripId` 로 서버 여행에 이어지므로, 기록을 볼 수
 * 있는지는 소속 여행 조회가 `404` 인지로 판정한다 (기록 상세).
 */
export function RecordsProvider({ children }) {
  const [records, setRecords] = useState(MOCK_RECORDS)
  // 기록은 아직 목업이지만 로그인 사용자만은 실제 세션에서 온다 (명세 §10.2).
  const { user } = useAuth()
  const currentUser = user ?? ANONYMOUS_USER

  /** 공개 범위는 판정하지 않는다. 소속 여행을 볼 수 있는지는 호출부가 서버에 묻는다. */
  const findRecord = useCallback(
    (recordId) => records.find((r) => String(r.id) === String(recordId)) ?? null,
    [records],
  )

  /** 한 여행의 기록. 여행을 볼 수 있으면 하위 기록은 전부 함께 보인다 (공통 명세 §3.5). */
  const recordsOfTrip = useCallback(
    (tripId) => records.filter((r) => String(r.tripId) === String(tripId)),
    [records],
  )

  /**
   * 서버에서 여행을 지운 뒤 그 여행의 목업 기록을 걷어낸다. 기록은 여행 없이 존재할 수
   * 없으므로 남겨 둘 자리가 없다 (공통 명세 §3.9).
   */
  const removeRecordsOfTrip = useCallback((tripId) => {
    setRecords((prev) => prev.filter((r) => String(r.tripId) !== String(tripId)))
  }, [])

  /** 기록은 여행 없이 존재할 수 없다. 소속 여행은 호출부가 정해 넘긴다 (공통 명세 §3.3). */
  const addRecord = useCallback(
    (record) => {
      setRecords((prev) => [{ ...record, author: currentUser, authorId: currentUser.id }, ...prev])
    },
    [currentUser],
  )

  const deleteRecord = useCallback((recordId) => {
    setRecords((prev) => prev.filter((r) => String(r.id) !== String(recordId)))
  }, [])

  /**
   * 기록 수정(장소·카테고리·평점·메모·사진). 고칠 때마다 updatedAt 을 올린다 —
   * 상세와 카드의 "(수정됨)" 표시가 이 값에 달려 있다.
   *
   * 소속 여행(tripId)은 이 함수로 바꾸지 않는다. 여행이 바뀌면 공개 범위가 함께 바뀌므로
   * 확인 절차가 따로 필요하다 (frontend 명세 §5.6.1, 미구현).
   *
   * 작성자 판정은 호출부(기록 상세)가 하고, 실제 차단 책임은 서버에 있다.
   */
  const updateRecord = useCallback((recordId, patch) => {
    setRecords((prev) =>
      prev.map((r) =>
        String(r.id) === String(recordId) ? { ...r, ...patch, updatedAt: today() } : r,
      ),
    )
  }, [])

  const value = useMemo(
    () => ({
      currentUser,
      records,
      recordsOfTrip,
      removeRecordsOfTrip,
      findRecord,
      addRecord,
      updateRecord,
      deleteRecord,
    }),
    [
      currentUser,
      records,
      recordsOfTrip,
      removeRecordsOfTrip,
      findRecord,
      addRecord,
      updateRecord,
      deleteRecord,
    ],
  )

  return <RecordsContext.Provider value={value}>{children}</RecordsContext.Provider>
}

export function useRecords() {
  const ctx = useContext(RecordsContext)
  if (!ctx) throw new Error('useRecords must be used within a RecordsProvider')
  return ctx
}
