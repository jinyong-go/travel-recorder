import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { CURRENT_USER, MOCK_RECORDS } from '../data/records.js'
import {
  GROUP_MEMBER_LIMIT,
  MOCK_GROUPS,
  inviteExpiryFromNow,
  newInviteToken,
} from '../data/groups.js'

const RecordsContext = createContext(null)

/**
 * 방문 기록과 공유 그룹의 클라이언트 상태.
 *
 * 백엔드 연동 전까지 목업 데이터로 동작하지만, 공개 범위 판정만큼은 서버와 같은 규칙으로 계산한다.
 * 화면에서 "보이면 안 되는 것" 이 보이는 상태로 만들어 두면 연동 시점에 그대로 남기 쉽다.
 * 물론 실제 차단 책임은 서버에 있다.
 */
export function RecordsProvider({ children }) {
  const [records, setRecords] = useState(MOCK_RECORDS)
  const [groups, setGroups] = useState(MOCK_GROUPS)
  const [invites, setInvites] = useState({})

  const currentUser = CURRENT_USER

  const myGroupIds = useMemo(
    () => groups.filter((g) => g.members.some((m) => m.id === currentUser.id)).map((g) => g.id),
    [groups, currentUser.id],
  )

  /** 볼 수 있는 조건은 셋 중 하나 — 내 기록이거나, 전체 공개이거나, 내가 속한 그룹으로 공유됐거나. */
  const canView = useCallback(
    (record, userId) => {
      if (userId != null && record.authorId === userId) return true
      if (record.visibility === 'PUBLIC') return true
      if (record.visibility !== 'GROUP' || userId == null) return false
      return record.sharedGroupIds.some((id) => myGroupIds.includes(id))
    },
    [myGroupIds],
  )

  const findRecord = useCallback(
    (recordId) => {
      const record = records.find((r) => String(r.id) === String(recordId))
      // 볼 권한이 없는 기록은 없는 기록과 똑같이 취급한다. 존재 여부를 화면 문구로도 흘리지 않는다.
      if (!record || !canView(record, currentUser.id)) return null
      return record
    },
    [records, canView, currentUser.id],
  )

  const listByScope = useCallback(
    (scope) => {
      switch (scope) {
        case 'mine':
          return records.filter((r) => r.authorId === currentUser.id)
        case 'shared':
          // 내 기록은 "내 기록" 탭에 이미 전부 있으므로 여기서 뺀다.
          return records.filter(
            (r) =>
              r.authorId !== currentUser.id &&
              r.visibility === 'GROUP' &&
              r.sharedGroupIds.some((id) => myGroupIds.includes(id)),
          )
        case 'public':
        default:
          return records.filter((r) => r.visibility === 'PUBLIC')
      }
    },
    [records, myGroupIds, currentUser.id],
  )

  const addRecord = useCallback(
    (record) => {
      setRecords((prev) => [{ ...record, author: currentUser, authorId: currentUser.id }, ...prev])
    },
    [currentUser],
  )

  /**
   * 공개 범위 변경. GROUP 이 아니게 되면 공유 그룹 목록을 비운다 —
   * 남겨 두면 나중에 다시 GROUP 으로 되돌렸을 때 예전 공유가 의도치 않게 되살아난다.
   */
  const changeVisibility = useCallback((recordId, visibility, groupIds = []) => {
    setRecords((prev) =>
      prev.map((r) =>
        String(r.id) === String(recordId)
          ? {
              ...r,
              visibility,
              sharedGroupIds: visibility === 'GROUP' ? groupIds : [],
              updatedAt: new Date().toISOString().slice(0, 10),
            }
          : r,
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

  /** 재발급하면 이전 토큰을 덮어써 즉시 무효가 된다. 그룹당 유효 링크는 1개뿐이다. */
  const issueInvite = useCallback((groupId) => {
    const invite = { token: newInviteToken(), expiresAt: inviteExpiryFromNow() }
    setInvites((prev) => ({ ...prev, [groupId]: invite }))
    return invite
  }, [])

  const revokeInvite = useCallback((groupId) => {
    setInvites((prev) => {
      const next = { ...prev }
      delete next[groupId]
      return next
    })
  }, [])

  const findInvite = useCallback(
    (token) => {
      const entry = Object.entries(invites).find(([, invite]) => invite.token === token)
      if (!entry) return null
      const [groupId, invite] = entry
      const group = groups.find((g) => String(g.id) === String(groupId))
      if (!group) return null
      const expired = new Date(invite.expiresAt) < new Date()
      return { ...invite, group, expired }
    },
    [invites, groups],
  )

  const acceptInvite = useCallback(
    (token) => {
      const invite = findInvite(token)
      if (!invite) return { error: 'NOT_FOUND' }
      if (invite.expired) return { error: 'EXPIRED' }

      const group = invite.group
      // 이미 멤버인 사용자의 재수락은 오류가 아니다. 링크를 두 번 눌렀다고 실패처럼 보일 이유가 없다.
      if (group.members.some((m) => m.id === currentUser.id)) return { groupId: group.id }
      if (group.members.length >= GROUP_MEMBER_LIMIT) return { error: 'LIMIT' }

      setGroups((prev) =>
        prev.map((g) =>
          g.id === group.id
            ? {
                ...g,
                members: [
                  ...g.members,
                  { ...currentUser, joinedAt: new Date().toISOString().slice(0, 10) },
                ],
              }
            : g,
        ),
      )
      return { groupId: group.id }
    },
    [findInvite, currentUser],
  )

  const value = useMemo(
    () => ({
      currentUser,
      records,
      listByScope,
      findRecord,
      addRecord,
      changeVisibility,
      deleteRecord,
      groups,
      myGroups,
      findGroup,
      createGroup,
      renameGroup,
      deleteGroup,
      removeMember,
      leaveGroup,
      invites,
      issueInvite,
      revokeInvite,
      findInvite,
      acceptInvite,
    }),
    [
      currentUser,
      records,
      listByScope,
      findRecord,
      addRecord,
      changeVisibility,
      deleteRecord,
      groups,
      myGroups,
      findGroup,
      createGroup,
      renameGroup,
      deleteGroup,
      removeMember,
      leaveGroup,
      invites,
      issueInvite,
      revokeInvite,
      findInvite,
      acceptInvite,
    ],
  )

  return <RecordsContext.Provider value={value}>{children}</RecordsContext.Provider>
}

export function useRecords() {
  const ctx = useContext(RecordsContext)
  if (!ctx) throw new Error('useRecords must be used within a RecordsProvider')
  return ctx
}
