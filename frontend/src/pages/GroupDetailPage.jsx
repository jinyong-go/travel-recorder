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
import { ArrowLeftIcon, PlusIcon } from '../components/icons.jsx'
import '../styles/detailPage.css'
import '../styles/groupPages.css'
import './GroupDetailPage.css'

const INVITE_ERROR = {
  USER_NOT_FOUND: '해당 이메일로 가입한 사용자가 없습니다. 주소를 다시 확인해주세요.',
  ALREADY_MEMBER: '이미 이 그룹의 멤버입니다.',
}

/**
 * 조회 실패 안내. **부재와 권한 부족을 구분해 보여 준다** — 여행·기록과 달리 그룹은 존재를
 * 숨기지 않기 때문이다.
 */
const LOAD_ERROR = {
  FORBIDDEN: { title: '이 그룹의 멤버만 볼 수 있습니다', desc: '초대를 받아 참여하면 볼 수 있어요.' },
  GROUP_NOT_FOUND: { title: '존재하지 않는 그룹입니다', desc: '주소를 다시 확인해주세요.' },
}

const DEFAULT_LOAD_ERROR = { title: '그룹을 불러오지 못했습니다', desc: '잠시 후 다시 시도해주세요.' }

/**
 * 확인 모달을 거치는 동작. `confirming.type` 으로 고르며, 문구와 요청과 끝난 뒤의 이동을 함께 둔다.
 *
 * `run(groupId, confirming)` 이 요청을 보낸다. `leavesPage` 면 그룹을 지웠거나 나가서 이 화면에
 * 남을 수 없으므로 그룹 목록으로 가고, 아니면 바뀐 멤버를 보이도록 그룹을 다시 읽는다.
 */
const CONFIRM_ACTIONS = {
  delete: {
    title: '그룹을 삭제할까요?',
    desc: '이 그룹으로만 공유한 여행은 나만 보기 상태가 됩니다. 여행과 기록 자체는 삭제되지 않습니다.',
    ok: '삭제',
    okBusy: '삭제하는 중…',
    run: (groupId) => api.deleteGroup(groupId),
    leavesPage: true,
  },
  leave: {
    title: '그룹에서 나갈까요?',
    desc: '이 그룹으로 공유된 여행은 더 이상 보이지 않습니다.',
    ok: '나가기',
    okBusy: '나가는 중…',
    run: (groupId) => api.leaveGroup(groupId),
    leavesPage: true,
  },
  member: {
    title: '멤버를 제외할까요?',
    desc: '제외된 멤버는 이 그룹으로 공유된 여행을 더 이상 볼 수 없습니다.',
    ok: '제외',
    okBusy: '제외하는 중…',
    run: (groupId, { memberId }) => api.removeMember(groupId, memberId),
    leavesPage: false,
  },
}

/**
 * 그룹 하나의 멤버와 초대. 소유자는 초대·초대 취소·멤버 제외·그룹 삭제를, 멤버는 나가기만 할 수 있다.
 */
export default function GroupDetailPage() {
  const { groupId } = useParams()
  const navigate = useNavigate()

  // 그룹 A 에서 B 로 바로 옮기면 A 의 응답이 늦게 올 수 있다. 훅이 가장 최근 요청의 응답만 남긴다.
  const loadGroup = useCallback(() => api.fetchGroup(groupId), [groupId])
  const { data: group, status: groupStatus, error: loadError, reload: reloadGroup } = useResource(loadGroup)

  const [email, setEmail] = useState('')
  const [inviteMessage, setInviteMessage] = useState(null)
  // 확인 모달에 올라 있는 동작. { type: 'delete' | 'leave' } 또는 { type: 'member', memberId }
  const [confirming, setConfirming] = useState(null)
  // 나가 있는 요청. 다른 버튼까지 막되 문구는 누른 버튼에서만 바꾼다.
  // action: 'send' | 'revoke' | 'confirm', id: 취소하는 초대의 id
  const [processing, setProcessing] = useState(null)
  const busy = processing !== null
  const isProcessing = (action, id) => processing?.action === action && processing.id === id

  // 대기 초대는 소유자만 본다. 비소유자에게는 호출 자체를 하지 않는다.
  const loadPending = useCallback((page) => api.fetchPendingInvites(groupId, page), [groupId])
  const pending = usePagedList(loadPending, Boolean(group?.isOwner))

  if (groupStatus === REQUEST_STATUS.ERROR || groupStatus === REQUEST_STATUS.MISSING) {
    const { title, desc } = errorMessage(loadError, LOAD_ERROR, DEFAULT_LOAD_ERROR)
    return <DetailStatus title={title} desc={desc} backTo="/groups" backLabel="그룹 목록으로" />
  }

  // 상태가 아니라 그룹 유무로 가른다. 멤버를 제외한 뒤 다시 읽는 동안 화면을 비우지 않는다.
  if (!group) return <DetailStatus title="그룹을 불러오는 중이에요…" />

  const isOwner = group.isOwner
  const isFull = group.memberCount >= group.memberLimit
  const pendingCount = pending.items.length

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
      // 이미 초대한 상대를 다시 초대한 것은 오류가 아니다. 서버가 201 이 아닌 200 으로 답한다.
      setInviteMessage({
        type: 'info',
        text: status === 200 ? '이미 초대한 상대입니다.' : '초대를 보냈습니다.',
      })
      pending.reload()
    } catch (err) {
      setInviteMessage({ type: 'error', text: errorMessage(err, INVITE_ERROR, '초대를 보내지 못했습니다.') })
    } finally {
      setProcessing(null)
    }
  }

  const handleRevoke = async (inviteId) => {
    setProcessing({ action: 'revoke', id: inviteId })
    try {
      await api.revokeInvite(group.id, inviteId)
    } catch {
      // 이미 처리된 초대일 수 있다. 어느 쪽이든 목록을 다시 읽으면 실제 상태로 맞춰진다.
      setInviteMessage({ type: 'error', text: '이미 처리되었거나 취소된 초대입니다.' })
    } finally {
      pending.reload()
      setProcessing(null)
    }
  }

  const pendingAction = confirming && CONFIRM_ACTIONS[confirming.type]

  const confirmAction = async () => {
    setProcessing({ action: 'confirm' })
    try {
      await pendingAction.run(group.id, confirming)
    } catch {
      setInviteMessage({ type: 'error', text: '요청을 처리하지 못했습니다. 다시 시도해주세요.' })
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

      <main className="groups-page">
        <div className="groups-inner">
          <Link to="/groups" className="back-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>

          <h1 className="groups-title">{group.name}</h1>
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
                  <span className="member-avatar" aria-hidden="true">
                    {member.name.slice(0, 1)}
                  </span>
                  <span className="member-name">
                    {member.name}
                    {member.id === group.owner.id && <span className="member-owner-tag">소유자</span>}
                  </span>
                  <span className="member-joined">{formatDate(member.joinedAt)} 참여</span>
                  {isOwner && member.id !== group.owner.id && (
                    <button
                      type="button"
                      className="member-remove"
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
              {/*
                정원 판정은 수락 시점에 하므로 정원이 차도 초대를 막지 않는다.
                막지 않는 대신, 보내기 전에 남은 자리와 그 결과를 먼저 알린다.
              */}
              <p className="invite-capacity">
                소유자를 포함해 최대 {group.memberLimit}명까지 참여할 수 있어요. 지금{' '}
                {group.memberCount}/{group.memberLimit}명
                {pendingCount > 0 && ` · 대기 중인 초대 ${pendingCount}건`}
                {isFull &&
                  ' — 정원이 차서 지금은 수락되지 않아요. 자리가 나면 같은 초대로 수락할 수 있어요.'}
              </p>
              {/* 자동완성이나 검색 결과를 붙이지 않는다. 가입자를 훑을 수 있는 화면이 된다. */}
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

              {/* 정원과 무관하게 초대를 보낼 수 있어 건수가 쌓인다. 빈 목록 안내는 없다 — 대기 건수는
                  위의 정원 안내가 이미 말한다. */}
              <PagedListBody
                list={pending}
                loadingText="불러오는 중이에요…"
                errorText="대기 중인 초대를 불러오지 못했습니다."
                noteClassName="invite-note"
                errorClassName="invite-error"
              >
                <ul className="invite-list">
                  {pending.items.map((item) => (
                    <li key={item.id} className="invite-item">
                      <span className="member-avatar" aria-hidden="true">
                        {item.invitee.name.slice(0, 1)}
                      </span>
                      <span className="member-name">{item.invitee.name}</span>
                      <span className="invite-sent-at">{formatDate(item.createdAt)} 보냄</span>
                      <button
                        type="button"
                        className="member-remove"
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
              className="detail-delete-btn"
              disabled={busy}
              onClick={() => setConfirming({ type: isOwner ? 'delete' : 'leave' })}
            >
              {isOwner ? '그룹 삭제' : '그룹 나가기'}
            </button>
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
