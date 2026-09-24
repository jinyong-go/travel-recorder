import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { MOCK_RECORDS } from '../data/records.js'
import { useAuth } from './AuthContext.jsx'
import { MOCK_GROUPS } from '../data/groups.js'
import { MOCK_TRIPS } from '../data/trips.js'

const today = () => new Date().toISOString().slice(0, 10)

/**
 * 비로그인 사용자. `id` 가 null 이라 어떤 소유자 판정에도 걸리지 않으므로,
 * 소유자 전용 UI 는 자연히 사라지고 공개 범위 판정은 비로그인 규칙을 탄다 (공통 명세 §3.5).
 */
const ANONYMOUS_USER = { id: null, name: '', email: null, profileImageUrl: null }

const RecordsContext = createContext(null)

/**
 * 여행·기록의 클라이언트 상태.
 *
 * 백엔드 연동 전까지 목업 데이터로 동작하지만, 공개 범위 판정만큼은 서버와 같은 규칙으로 계산한다.
 * 화면에서 "보이면 안 되는 것" 이 보이는 상태로 만들어 두면 연동 시점에 그대로 남기 쉽다.
 * 물론 실제 차단 책임은 서버에 있다.
 *
 * **그룹과 초대는 여기 없다.** 그쪽은 서버를 쓰므로 `GroupsContext` 가 맡는다 (명세 §10.2).
 */
export function RecordsProvider({ children }) {
  const [trips, setTrips] = useState(MOCK_TRIPS)
  const [records, setRecords] = useState(MOCK_RECORDS)
  // 여행·기록은 아직 목업이지만 로그인 사용자만은 실제 세션에서 온다 (명세 §10.2).
  const { user } = useAuth()
  const currentUser = user ?? ANONYMOUS_USER

  /**
   * 목업 여행이 공유 대상으로 가리키는 그룹 (`MOCK_TRIPS.sharedGroupIds`).
   *
   * **서버 그룹이 아니다.** 그룹 화면은 `GroupsContext` 로 실제 API 를 쓰지만, 목업 여행의
   * `sharedGroupIds` 는 `MOCK_GROUPS` 의 id 를 가리키고 있어 두 id 체계가 섞이면 GROUP 여행이
   * 통째로 사라진다. 여행을 연동할 때 이 값과 `MOCK_GROUPS` 를 함께 걷어낸다 (명세 §10.2).
   */
  const myGroups = useMemo(
    () => MOCK_GROUPS.filter((g) => g.members.some((m) => m.id === currentUser.id)),
    [currentUser.id],
  )

  const myGroupIds = useMemo(() => myGroups.map((g) => g.id), [myGroups])

  /**
   * 여행을 볼 수 있는 조건은 셋 중 하나 — 내 여행이거나, 전체 공개이거나,
   * 내가 속한 그룹으로 공유됐거나.
   *
   * **판정은 여행에만 한다.** 기록은 공개 범위를 갖지 않으므로 소속 여행의 판정 결과를
   * 그대로 따른다 (공통 명세 §3.5).
   */
  const canViewTrip = useCallback(
    (trip, userId) => {
      if (!trip) return false
      if (userId != null && trip.ownerId === userId) return true
      if (trip.visibility === 'PUBLIC') return true
      if (trip.visibility !== 'GROUP' || userId == null) return false
      return trip.sharedGroupIds.some((id) => myGroupIds.includes(id))
    },
    [myGroupIds],
  )

  /** 기록의 소속 여행. tripId 는 필수라 목록에 없는 여행을 가리키는 기록은 없다. */
  const tripOf = useCallback(
    (record) => trips.find((t) => String(t.id) === String(record?.tripId)) ?? null,
    [trips],
  )

  const findRecord = useCallback(
    (recordId) => {
      const record = records.find((r) => String(r.id) === String(recordId))
      // 볼 권한이 없는 기록은 없는 기록과 똑같이 취급한다. 존재 여부를 화면 문구로도 흘리지 않는다.
      if (!record || !canViewTrip(tripOf(record), currentUser.id)) return null
      return record
    },
    [records, canViewTrip, tripOf, currentUser.id],
  )

  const findTrip = useCallback(
    (tripId) => {
      const trip = trips.find((t) => String(t.id) === String(tripId))
      // 볼 수 없는 여행도 없는 여행과 똑같이 취급한다 (공통 명세 §2.6).
      if (!trip || !canViewTrip(trip, currentUser.id)) return null
      return trip
    },
    [trips, canViewTrip, currentUser.id],
  )

  /** 한 여행의 기록. 여행을 볼 수 있으면 하위 기록은 전부 함께 보인다 (공통 명세 §3.5). */
  const recordsOfTrip = useCallback(
    (tripId) => records.filter((r) => String(r.tripId) === String(tripId)),
    [records],
  )

  const myTrips = useMemo(
    () => trips.filter((t) => t.ownerId === currentUser.id),
    [trips, currentUser.id],
  )

  const listTripsByScope = useCallback(
    (scope) => {
      switch (scope) {
        case 'mine':
          return trips.filter((t) => t.ownerId === currentUser.id)
        case 'shared':
          // 내 여행은 "내 여행" 탭에 이미 전부 있으므로 여기서 뺀다.
          return trips.filter(
            (t) =>
              t.ownerId !== currentUser.id &&
              t.visibility === 'GROUP' &&
              t.sharedGroupIds.some((id) => myGroupIds.includes(id)),
          )
        case 'public':
        default:
          return trips.filter((t) => t.visibility === 'PUBLIC')
      }
    },
    [trips, myGroupIds, currentUser.id],
  )

  const createTrip = useCallback(
    (trip) => {
      const created = {
        ...trip,
        id: Date.now(),
        ownerId: currentUser.id,
        owner: currentUser,
        // 대표 사진은 하위 기록의 사진 중에서 고르는 값이라 생성 시점에는 지정할 수 없다 (§3.2).
        coverPhotoUrl: null,
        sharedGroupIds: trip.visibility === 'GROUP' ? (trip.sharedGroupIds ?? []) : [],
        createdAt: new Date().toISOString().slice(0, 10),
      }
      setTrips((prev) => [created, ...prev])
      return created
    },
    [currentUser],
  )

  /** 여행 정보 수정. 공개 범위는 여기서 바꾸지 않는다 — changeTripVisibility 의 몫이다. */
  const updateTrip = useCallback((tripId, patch) => {
    setTrips((prev) =>
      prev.map((t) => (String(t.id) === String(tripId) ? { ...t, ...patch } : t)),
    )
  }, [])

  /**
   * 여행 삭제. **하위 기록도 함께 지운다** — 기록은 여행 없이 존재할 수 없으므로 남겨 둘
   * 자리가 없다 (공통 명세 §3.9).
   *
   * 소유자 판정은 호출부(여행 상세)가 하고, 실제 차단 책임은 서버에 있다.
   */
  const deleteTrip = useCallback((tripId) => {
    setTrips((prev) => prev.filter((t) => String(t.id) !== String(tripId)))
    setRecords((prev) => prev.filter((r) => String(r.tripId) !== String(tripId)))
  }, [])

  /** 기록은 여행 없이 존재할 수 없다. 소속 여행은 호출부가 정해 넘긴다 (공통 명세 §3.3). */
  const addRecord = useCallback(
    (record) => {
      setRecords((prev) => [{ ...record, author: currentUser, authorId: currentUser.id }, ...prev])
    },
    [currentUser],
  )

  /**
   * 여행의 공개 범위 변경. GROUP 이 아니게 되면 공유 그룹 목록을 비운다 —
   * 남겨 두면 나중에 다시 GROUP 으로 되돌렸을 때 예전 공유가 의도치 않게 되살아난다.
   *
   * 하위 기록은 손대지 않는다. 범위는 여행 한 곳에만 있어서 여행만 바꾸면 전부 따라온다.
   */
  const changeTripVisibility = useCallback((tripId, visibility, groupIds = []) => {
    setTrips((prev) =>
      prev.map((t) =>
        String(t.id) === String(tripId)
          ? { ...t, visibility, sharedGroupIds: visibility === 'GROUP' ? groupIds : [] }
          : t,
      ),
    )
  }, [])

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
      trips,
      myTrips,
      listTripsByScope,
      findTrip,
      recordsOfTrip,
      tripOf,
      createTrip,
      updateTrip,
      deleteTrip,
      changeTripVisibility,
      findRecord,
      addRecord,
      updateRecord,
      deleteRecord,
      // 여행 화면의 공유 그룹 선택·표시용. 목업 그룹이다 (위 myGroups 주석 참고).
      myGroups,
    }),
    [
      currentUser,
      records,
      trips,
      myTrips,
      listTripsByScope,
      findTrip,
      recordsOfTrip,
      tripOf,
      createTrip,
      updateTrip,
      deleteTrip,
      changeTripVisibility,
      findRecord,
      addRecord,
      updateRecord,
      deleteRecord,
      myGroups,
    ],
  )

  return <RecordsContext.Provider value={value}>{children}</RecordsContext.Provider>
}

export function useRecords() {
  const ctx = useContext(RecordsContext)
  if (!ctx) throw new Error('useRecords must be used within a RecordsProvider')
  return ctx
}
