import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { errorMessage } from '../api/client.js'
import * as api from '../api/groups.js'
import { REQUEST_STATUS } from '../api/requestStatus.js'
import usePagedList from '../hooks/usePagedList.js'
import useResource from '../hooks/useResource.js'
import { formatDate } from '../utils/date.js'
import AppHeader from '../components/AppHeader.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import DetailStatus from '../components/DetailStatus.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import UserAvatar from '../components/UserAvatar.jsx'
import { ArrowLeftIcon, PlusIcon } from '../components/icons.jsx'
import '../styles/page.css'
import '../styles/detailPage.css'
import '../styles/groupPages.css'
import './GroupDetailPage.css'
import { INVITE_GONE_MESSAGE } from '../data/invites.js'

const INVITE_ERROR = {
  USER_NOT_FOUND: '해당 이메일로 가입한 사용자가 없어요. 주소를 다시 확인해주세요.',
  ALREADY_MEMBER: '이미 이 그룹의 멤버예요.',
}

/** 그룹 조회 실패 안내 */
const LOAD_ERROR = {
  FORBIDDEN: { title: '이 그룹의 멤버만 볼 수 있어요', desc: '초대를 받아 참여하면 볼 수 있어요.' },
  GROUP_NOT_FOUND: { title: '존재하지 않는 그룹이에요', desc: '주소를 다시 확인해주세요.' },
}

const DEFAULT_LOAD_ERROR = { title: '그룹을 불러오지 못했어요', desc: '잠시 후 다시 시도해주세요.' }

/** 확인 모달을 거치는 동작 */
const CONFIRM_ACTIONS = {
  delete: {
    title: '그룹을 삭제할까요?',
    desc: `이 그룹으로만 공유한 여행은 나만 보기 상태가 돼요. 여행과 기록 자체는 삭제되지 않아요.`,
    ok: '삭제',
    okBusy: '삭제하는 중…',
    run: (groupId) => api.deleteGroup(groupId),
    leavesPage: true,
  },
  leave: {
    title: '그룹에서 나갈까요?',
    desc: `이 그룹으로 공유된 여행은 더 이상 보이지 않아요. 내가 이 그룹에 공유한 여행도 더 이상 이 그룹에 보이지 않아요.`,
    ok: '나가기',
    okBusy: '나가는 중…',
    run: (groupId) => api.leaveGroup(groupId),
    leavesPage: true,
  },
  member: {
    title: '멤버를 제외할까요?',
    desc: `제외된 멤버는 이 그룹으로 공유된 여행을 더 이상 볼 수 없어요. 이 멤버가 이 그룹에 공유한 여행도 더 이상 이 그룹에 보이지 않아요.`,
    ok: '제외',
    okBusy: '제외하는 중…',
    run: (groupId, { memberId }) => api.removeMember(groupId, memberId),
    leavesPage: false,
  },
}

/** 그룹 상세 — 멤버와 초대 관리 */
export default function GroupDetailPage() {
  const { groupId } = useParams()
  const navigate = useNavigate()

  const loadGroup = useCallback(() => api.fetchGroup(groupId), [groupId])
  const { data: group, status: groupStatus, error: loadError, reload: reloadGroup } = useResource(loadGroup)

  const [email, setEmail] = useState('')
  const [inviteMessage, setInviteMessage] = useState(null)
  // 나가기·삭제·제외 실패 문구
  const [actionError, setActionError] = useState(null)
  // 확인 모달에 올린 동작
  const [confirming, setConfirming] = useState(null)
  // 진행 중인 요청
  const [processing, setProcessing] = useState(null)
  const busy = processing !== null
  const isProcessing = (action, id) => processing?.action === action && processing.id === id

  const loadPending = useCallback((page) => api.fetchPendingInvites(groupId, page), [groupId])
  const pending = usePagedList(loadPending, Boolean(group?.isOwner))

  if (groupStatus === REQUEST_STATUS.ERROR || groupStatus === REQUEST_STATUS.MISSING) {
    const { title, desc } = errorMessage(loadError, LOAD_ERROR, DEFAULT_LOAD_ERROR)
    return <DetailStatus title={title} desc={desc} backTo="/groups" backLabel="그룹 목록으로" />
  }

  if (!group) return <DetailStatus title="그룹을 불러오는 중이에요…" />

  const isOwner = group.isOwner
  const isFull = group.memberCount >= group.memberLimit
  const pendingCount = pending.total ?? 0

  const handleSend = async (e) => {
    e.preventDefault()
    const trimmed = email.trim()
    if (!trimmed) {
      setInviteMessage({ type: 'error', text: '초대할 상대의 이메일을 입력해주세요.' })
      return
    }

    setProcessing({ action: 'send' })
    try {
      const { status } = await api.sendInvite(group.id, trimmed)
      setEmail('')
      // 이미 초대한 상대
      setInviteMessage({
        type: 'info',
        text: status === 200 ? '이미 초대한 상대예요.' : '초대를 보냈어요.',
      })
      pending.reload()
    } catch (err) {
      setInviteMessage({ type: 'error', text: errorMessage(err, INVITE_ERROR, '초대를 보내지 못했어요.') })
    } finally {
      setProcessing(null)
    }
  }

  const handleRevoke = async (inviteId) => {
    setProcessing({ action: 'revoke', id: inviteId })
    try {
      await api.revokeInvite(group.id, inviteId)
    } catch {
      setInviteMessage({ type: 'error', text: INVITE_GONE_MESSAGE })
    } finally {
      pending.reload()
      setProcessing(null)
    }
  }

  const pendingAction = confirming && CONFIRM_ACTIONS[confirming.type]

  const confirmAction = async () => {
    setProcessing({ action: 'confirm' })
    setActionError(null)
    try {
      await pendingAction.run(group.id, confirming)
    } catch {
      setActionError('요청을 처리하지 못했어요. 다시 시도해주세요.')
      return
    } finally {
      setProcessing(null)
      setConfirming(null)
    }

    if (pendingAction.leavesPage) navigate('/groups')
    else reloadGroup()
  }

  return (
    <>
      <AppHeader />

      <main className="page">
        <div className="page-inner">
          <Link to="/groups" className="back-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>

          <h1 className="page-title group-detail-name">{group.name}</h1>
          {group.memo && <p className="group-memo">{group.memo}</p>}
          <p className="groups-desc">
            멤버 {group.memberCount}/{group.memberLimit}명
            {isOwner && pendingCount > 0 && ` · 대기 중인 초대 ${pendingCount}건`} ·{' '}
            {isOwner ? '내가 만든 그룹' : '참여 중인 그룹'}
          </p>

          <section className="group-section">
            <h2 className="group-section-title">멤버</h2>
            <ul className="member-list">
              {group.members.map((member) => (
                <li key={member.id} className="member-item">
                  <UserAvatar user={member} />
                  <span className="member-name">
                    {member.name}
                    {member.id === group.owner.id && <span className="member-owner-tag">소유자</span>}
                  </span>
                  <span className="member-joined">{formatDate(member.joinedAt)} 참여</span>
                  {isOwner && member.id !== group.owner.id && (
                    <button
                      type="button"
                      className="btn-secondary btn-sm"
                      disabled={busy}
                      onClick={() => setConfirming({ type: 'member', memberId: member.id })}
                    >
                      제외
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </section>

          {isOwner && (
            <section className="group-section">
              <h2 className="group-section-title">초대</h2>
              <p className="invite-capacity">
                소유자를 포함해 최대 {group.memberLimit}명까지 참여할 수 있어요. 지금{' '}
                {group.memberCount}/{group.memberLimit}명
                {pendingCount > 0 && ` · 대기 중인 초대 ${pendingCount}건`}
                {isFull &&
                  ' — 정원이 차서 지금은 수락되지 않아요. 자리가 나면 같은 초대로 수락할 수 있어요.'}
              </p>
              <form className="invite-form" onSubmit={handleSend}>
                <label className="sr-only" htmlFor="invite-email">
                  초대할 상대의 이메일
                </label>
                <input
                  id="invite-email"
                  type="email"
                  value={email}
                  placeholder="friend@example.com"
                  autoComplete="off"
                  onChange={(e) => {
                    setEmail(e.target.value)
                    setInviteMessage(null)
                  }}
                />
                <button type="submit" className="btn-primary" disabled={busy}>
                  <PlusIcon />
                  {isProcessing('send') ? '보내는 중…' : '초대 보내기'}
                </button>
              </form>
              <p className="invite-note">
                상대가 이 서비스에 가입할 때 쓴 이메일을 정확히 입력해주세요.
              </p>
              {inviteMessage && (
                <p className={inviteMessage.type === 'error' ? 'invite-error' : 'invite-info'}>
                  {inviteMessage.text}
                </p>
              )}

              <PagedListBody
                list={pending}
                loadingText="불러오는 중이에요…"
                errorText="대기 중인 초대를 불러오지 못했어요."
                noteClassName="invite-note"
                errorClassName="invite-error"
              >
                <ul className="invite-list">
                  {pending.items.map((item) => (
                    <li key={item.id} className="invite-item">
                      <UserAvatar user={item.invitee} />
                      <span className="member-name">{item.invitee.name}</span>
                      <span className="invite-sent-at">{formatDate(item.createdAt)} 보냄</span>
                      <button
                        type="button"
                        className="btn-secondary btn-sm"
                        disabled={busy}
                        onClick={() => handleRevoke(item.id)}
                      >
                        {isProcessing('revoke', item.id) ? '취소하는 중…' : '초대 취소'}
                      </button>
                    </li>
                  ))}
                </ul>
              </PagedListBody>
            </section>
          )}

          <section className="group-section group-danger-zone">
            <button
              type="button"
              className="btn-danger-outline"
              disabled={busy}
              onClick={() => setConfirming({ type: isOwner ? 'delete' : 'leave' })}
            >
              {isOwner ? '그룹 삭제' : '그룹 나가기'}
            </button>
            {actionError && <p className="invite-error">{actionError}</p>}
          </section>
        </div>
      </main>

      {pendingAction && (
        <ConfirmDialog
          title={pendingAction.title}
          confirmLabel={pendingAction.ok}
          busyLabel={pendingAction.okBusy}
          busy={isProcessing('confirm')}
          onConfirm={confirmAction}
          onCancel={() => setConfirming(null)}
        >
          {pendingAction.desc}
        </ConfirmDialog>
      )}
    </>
  )
}
