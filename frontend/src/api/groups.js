import { apiFetch, apiPath } from './client.js'

/** 그룹·초대 API */

export const fetchGroups = (page) => apiFetch(apiPath`/api/groups?page=${page}`)

export const fetchGroup = (groupId) => apiFetch(apiPath`/api/groups/${groupId}`)

export const createGroup = (name, memo) =>
  apiFetch('/api/groups', { method: 'POST', body: { name, memo } })

/** 그룹 이름·메모 변경 */
export const updateGroup = (groupId, name, memo) =>
  apiFetch(apiPath`/api/groups/${groupId}`, { method: 'PUT', body: { name, memo } })

export const deleteGroup = (groupId) => apiFetch(apiPath`/api/groups/${groupId}`, { method: 'DELETE' })

export const removeMember = (groupId, userId) =>
  apiFetch(apiPath`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' })

export const leaveGroup = (groupId) =>
  apiFetch(apiPath`/api/groups/${groupId}/members/me`, { method: 'DELETE' })

export const fetchPendingInvites = (groupId, page) =>
  apiFetch(apiPath`/api/groups/${groupId}/invites?page=${page}`)

/** 이메일로 초대를 보낸다 */
export const sendInvite = (groupId, email) =>
  apiFetch(apiPath`/api/groups/${groupId}/invites`, { method: 'POST', body: { email }, withStatus: true })

export const revokeInvite = (groupId, inviteId) =>
  apiFetch(apiPath`/api/groups/${groupId}/invites/${inviteId}`, { method: 'DELETE' })

export const fetchReceivedInvites = (page) => apiFetch(apiPath`/api/invites?page=${page}`)

export const fetchSentInvites = (page) => apiFetch(apiPath`/api/invites/sent?page=${page}`)

/** 받은 초대 또는 보낸 초대의 이력 */
export const fetchInviteHistory = (role, page) =>
  apiFetch(apiPath`/api/invites/history?role=${role}&page=${page}`)

export const acceptInvite = (inviteId) =>
  apiFetch(apiPath`/api/invites/${inviteId}/accept`, { method: 'POST' })

export const rejectInvite = (inviteId) =>
  apiFetch(apiPath`/api/invites/${inviteId}/reject`, { method: 'POST' })
