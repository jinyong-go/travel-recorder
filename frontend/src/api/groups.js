import { apiFetch } from './client.js'

/**
 * 그룹·초대 API 호출 모음 (backend §4.7, §4.8).
 *
 * 응답을 가공하지 않고 그대로 흘린다. 화면이 쓰는 모양으로 바꾸는 일은 컨텍스트가 맡으며,
 * 여기서 한 번 더 감싸면 백엔드 응답과 화면 사이에 이름이 둘로 늘어난다.
 *
 * 목록은 모두 `PageResponse` 다 — `{ content, page, size, totalElements, totalPages }`.
 */

export const fetchGroups = () => apiFetch('/api/groups')

export const fetchGroup = (groupId) => apiFetch(`/api/groups/${groupId}`)

export const createGroup = (name, memo) =>
  apiFetch('/api/groups', { method: 'POST', body: { name, memo } })

/**
 * 이름·메모 변경. **둘을 함께 덮어쓰므로 메모를 빼고 보내면 기존 메모가 지워진다** (backend §4.7).
 *
 * 아직 화면에 진입점이 없다 (명세 §10.2). 수정 UI 가 생기면 그대로 쓰면 된다.
 */
export const updateGroup = (groupId, name, memo) =>
  apiFetch(`/api/groups/${groupId}`, { method: 'PUT', body: { name, memo } })

export const deleteGroup = (groupId) => apiFetch(`/api/groups/${groupId}`, { method: 'DELETE' })

export const removeMember = (groupId, userId) =>
  apiFetch(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' })

export const leaveGroup = (groupId) =>
  apiFetch(`/api/groups/${groupId}/members/me`, { method: 'DELETE' })

export const fetchPendingInvites = (groupId, page) =>
  apiFetch(`/api/groups/${groupId}/invites?page=${page}`)

/**
 * 이메일로 초대 보내기.
 *
 * 상태 코드를 함께 받는다. 새로 만들어졌으면 `201`, 대기 중인 초대가 이미 있어 그것을 그대로
 * 돌려준 것이면 `200` 이고 본문은 구분되지 않는다 (backend §4.8). 화면은 후자를 오류가 아니라
 * "이미 초대한 상대입니다" 로 알린다 (명세 §5.8.3).
 */
export const sendInvite = (groupId, email) =>
  apiFetch(`/api/groups/${groupId}/invites`, { method: 'POST', body: { email }, withStatus: true })

export const revokeInvite = (groupId, inviteId) =>
  apiFetch(`/api/groups/${groupId}/invites/${inviteId}`, { method: 'DELETE' })

export const fetchReceivedInvites = (page) => apiFetch(`/api/invites?page=${page}`)

export const fetchSentInvites = (page) => apiFetch(`/api/invites/sent?page=${page}`)

/** `role` 은 `RECEIVED` 또는 `SENT`. 어느 쪽이든 본인이 당사자인 이력만 온다 (backend §4.8). */
export const fetchInviteHistory = (role, page) =>
  apiFetch(`/api/invites/history?role=${role}&page=${page}`)

export const acceptInvite = (inviteId) =>
  apiFetch(`/api/invites/${inviteId}/accept`, { method: 'POST' })

export const rejectInvite = (inviteId) =>
  apiFetch(`/api/invites/${inviteId}/reject`, { method: 'POST' })
