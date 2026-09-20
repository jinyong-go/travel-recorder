import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { CURRENT_USER, MOCK_RECORDS, findUserByEmail, findUserById } from '../data/records.js'
import { GROUP_MEMBER_LIMIT, MOCK_GROUPS, MOCK_INVITES } from '../data/groups.js'
import { MOCK_TRIPS } from '../data/trips.js'

const today = () => new Date().toISOString().slice(0, 10)

const RecordsContext = createContext(null)

/**
 * 여행·기록과 공유 그룹의 클라이언트 상태.
 *
 * 백엔드 연동 전까지 목업 데이터로 동작하지만, 공개 범위 판정만큼은 서버와 같은 규칙으로 계산한다.
 * 화면에서 "보이면 안 되는 것" 이 보이는 상태로 만들어 두면 연동 시점에 그대로 남기 쉽다.
 * 물론 실제 차단 책임은 서버에 있다.
 */
export function RecordsProvider({ children }) {
  const [trips, setTrips] = useState(MOCK_TRIPS)
  const [records, setRecords] = useState(MOCK_RECORDS)
  const [groups, setGroups] = useState(MOCK_GROUPS)
  // 초대는 소유자가 특정 사용자 앞으로 보내는 행이다. 토큰도 만료도 없다 (공통 명세 §3.7).
  const [invites, setInvites] = useState(MOCK_INVITES)

  const currentUser = CURRENT_USER

  const myGroupIds = useMemo(
    () => groups.filter((g) => g.members.some((m) => m.id === currentUser.id)).map((g) => g.id),
    [groups, currentUser.id],
  )

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

  const myGroups = useMemo(
    () => groups.filter((g) => g.members.some((m) => m.id === currentUser.id)),
    [groups, currentUser.id],
  )

  const findGroup = useCallback(
    (groupId) => {
      const group = groups.find((g) => String(g.id) === String(groupId))
      // 멤버가 아니면 그룹의 존재도 알리지 않는다.
      if (!group || !group.members.some((m) => m.id === currentUser.id)) return null
      return group
    },
    [groups, currentUser.id],
  )

  const createGroup = useCallback(
    (name) => {
      const group = {
        id: Date.now(),
        name: name.trim(),
        ownerId: currentUser.id,
        // 소유자도 멤버 행을 가진다. 인원 계산과 권한 판정을 한 경로로 모으기 위해서다.
        members: [{ ...currentUser, joinedAt: new Date().toISOString().slice(0, 10) }],
      }
      setGroups((prev) => [...prev, group])
      return group
    },
    [currentUser],
  )

  const renameGroup = useCallback((groupId, name) => {
    setGroups((prev) =>
      prev.map((g) => (String(g.id) === String(groupId) ? { ...g, name: name.trim() } : g)),
    )
  }, [])

  /** 그룹을 지우면 그 그룹으로만 공유되던 기록은 사실상 비공개가 된다. 기록 자체는 남는다. */
  const deleteGroup = useCallback((groupId) => {
    setGroups((prev) => prev.filter((g) => String(g.id) !== String(groupId)))
    // 대기 중인 초대도 함께 사라진다. 받는 쪽 목록에 주인 없는 초대가 남지 않아야 한다 (§3.9).
    setInvites((prev) => prev.filter((i) => String(i.groupId) !== String(groupId)))
    setRecords((prev) =>
      prev.map((r) => ({
        ...r,
        sharedGroupIds: r.sharedGroupIds.filter((id) => String(id) !== String(groupId)),
      })),
    )
  }, [])

  const removeMember = useCallback((groupId, userId) => {
    setGroups((prev) =>
      prev.map((g) =>
        String(g.id) === String(groupId)
          ? { ...g, members: g.members.filter((m) => m.id !== userId) }
          : g,
      ),
    )
  }, [])

  const leaveGroup = useCallback(
    (groupId) => removeMember(groupId, currentUser.id),
    [removeMember, currentUser.id],
  )

  /**
   * 이메일로 초대를 보낸다. 성공하면 `{ invite, duplicated }`, 실패하면 `{ error }` 다.
   *
   * 가입자가 없으면 `USER_NOT_FOUND` 로 그 사실을 그대로 알린다 — 소유자가 오타를 알아차릴
   * 유일한 수단이며, 이 응답이 이메일의 가입 여부를 드러내는 것은 감수한 비용이다
   * (공통 명세 §7.2). 대기 중인 초대가 이미 있으면 새로 만들지 않고 그것을 돌려준다.
   */
  const sendInvite = useCallback(
    (groupId, email) => {
      const group = groups.find((g) => String(g.id) === String(groupId))
      if (!group) return { error: 'NOT_FOUND' }

      const invitee = findUserByEmail(email)
      if (!invitee) return { error: 'USER_NOT_FOUND' }
      if (group.members.some((m) => m.id === invitee.id)) return { error: 'ALREADY_MEMBER' }

      const existing = invites.find(
        (i) => i.groupId === group.id && i.inviteeId === invitee.id,
      )
      if (existing) return { invite: existing, duplicated: true }

      const invite = {
        id: Date.now(),
        groupId: group.id,
        inviteeId: invitee.id,
        invitedById: currentUser.id,
        createdAt: today(),
      }
      setInvites((prev) => [...prev, invite])
      return { invite, duplicated: false }
    },
    [groups, invites, currentUser.id],
  )

  /**
   * 소유자가 보는 대기 초대 목록.
   *
   * 상대 정보는 이름·프로필 사진까지만 담는다. 소유자가 직접 입력한 이메일이라도 화면으로
   * 되돌려주지 않는다 (공통 명세 §3.7).
   */
  const pendingInvites = useCallback(
    (groupId) =>
      invites
        .filter((i) => String(i.groupId) === String(groupId))
        .map((i) => ({ id: i.id, invitee: findUserById(i.inviteeId), createdAt: i.createdAt })),
    [invites],
  )

  /** 내가 받은 초대. 수락 전이므로 그룹명·초대자·보낸 시각까지만 담는다 (공통 명세 §3.7). */
  const receivedInvites = useMemo(
    () =>
      invites
        .filter((i) => i.inviteeId === currentUser.id)
        .map((i) => {
          const group = groups.find((g) => g.id === i.groupId)
          if (!group) return null
          return {
            id: i.id,
            group: { id: group.id, name: group.name },
            invitedBy: findUserById(i.invitedById),
            createdAt: i.createdAt,
          }
        })
        .filter(Boolean),
    [invites, groups, currentUser.id],
  )

  /**
   * 초대를 수락해 멤버가 된다. 성공하면 `{ groupId }`, 실패하면 `{ error }` 다.
   *
   * 정원 판정은 이 시점에 한다 — 대기 중인 초대는 자리를 차지하지 않기 때문이다.
   * 정원이 차서 거부되어도 **초대 행은 남긴다.** 자리가 나면 같은 초대로 다시 수락할 수
   * 있어야 한다 (공통 명세 §3.7).
   */
  const acceptInvite = useCallback(
    (inviteId) => {
      const invite = invites.find((i) => String(i.id) === String(inviteId))
      // 당사자가 아닌 초대는 존재하지 않는 것과 같게 다룬다 (공통 명세 §2.6).
      if (!invite || invite.inviteeId !== currentUser.id) return { error: 'NOT_FOUND' }

      const group = groups.find((g) => g.id === invite.groupId)
      if (!group) return { error: 'NOT_FOUND' }
      if (group.members.length >= GROUP_MEMBER_LIMIT) return { error: 'LIMIT' }

      setGroups((prev) =>
        prev.map((g) =>
          g.id === group.id
            ? { ...g, members: [...g.members, { ...currentUser, joinedAt: today() }] }
            : g,
        ),
      )
      setInvites((prev) => prev.filter((i) => i.id !== invite.id))
      return { groupId: group.id }
    },
    [invites, groups, currentUser],
  )

  /** 받은 사람이 거절한다. 흔적을 남기지 않으므로 소유자는 거절 사실을 알 수 없다 (§3.7). */
  const rejectInvite = useCallback(
    (inviteId) => {
      setInvites((prev) =>
        prev.filter(
          (i) => !(String(i.id) === String(inviteId) && i.inviteeId === currentUser.id),
        ),
      )
    },
    [currentUser.id],
  )

  /** 보낸 소유자가 철회한다. 거절과 결과는 같고 누가 하느냐만 다르다. */
  const revokeInvite = useCallback((inviteId) => {
    setInvites((prev) => prev.filter((i) => String(i.id) !== String(inviteId)))
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
      deleteRecord,
      groups,
      myGroups,
      findGroup,
      createGroup,
      renameGroup,
      deleteGroup,
      removeMember,
      leaveGroup,
      sendInvite,
      pendingInvites,
      receivedInvites,
      acceptInvite,
      rejectInvite,
      revokeInvite,
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
      deleteRecord,
      groups,
      myGroups,
      findGroup,
      createGroup,
      renameGroup,
      deleteGroup,
      removeMember,
      leaveGroup,
      sendInvite,
      pendingInvites,
      receivedInvites,
      acceptInvite,
      rejectInvite,
      revokeInvite,
    ],
  )

  return <RecordsContext.Provider value={value}>{children}</RecordsContext.Provider>
}

export function useRecords() {
  const ctx = useContext(RecordsContext)
  if (!ctx) throw new Error('useRecords must be used within a RecordsProvider')
  return ctx
}
