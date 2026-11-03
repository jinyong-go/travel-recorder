import { useCallback, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client.js'
import * as api from '../api/groups.js'
import usePagedList from '../hooks/usePagedList.js'
import { formatDate } from '../utils/date.js'
import AppHeader from '../components/AppHeader.jsx'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import PagedListBody from '../components/PagedListBody.jsx'
import { ArrowLeftIcon } from '../components/icons.jsx'
import '../styles/page.css'
import '../styles/groupPages.css'
import './InvitesPage.css'
import { INVITE_GONE_MESSAGE } from '../data/invites.js'
import { JOINED_GROUP_LIMIT } from '../config/groupLimits.js'

/** 끝난 초대의 결과 표기 */
const OUTCOME_LABEL = {
  received: {
    ACCEPTED: '수락함',
    REJECTED: '거절함',
    REVOKED: '취소됨',
    GROUP_DELETED: '그룹이 삭제됨',
  },
  sent: {
    ACCEPTED: '수락됨',
    REJECTED: '거절됨',
    REVOKED: '취소함',
    GROUP_DELETED: '그룹이 삭제됨',
  },
}

/** 수락 실패 안내 */
const ACCEPT_ERROR = {
  GROUP_MEMBER_LIMIT_EXCEEDED: '그룹 정원이 가득 찼어요. 자리가 나면 다시 수락할 수 있어요.',
  GROUP_LIMIT_EXCEEDED: `참여할 수 있는 그룹은 ${JOINED_GROUP_LIMIT}개까지예요. 다른 그룹에서 나가면 수락할 수 있어요.`,
}

/** 초대함 — 받은 초대와 보낸 초대 */
export default function InvitesPage({ tab }) {
  const navigate = useNavigate()
  const [inviteError, setInviteError] = useState('')
  const [rejecting, setRejecting] = useState(null)
  // 진행 중인 요청
  const [processing, setProcessing] = useState(null)
  const busy = processing !== null
  const isProcessing = (action, id) => processing?.action === action && processing.id === id

  const loadPending = useCallback(
    (page) => (tab === 'received' ? api.fetchReceivedInvites(page) : api.fetchSentInvites(page)),
    [tab],
  )
  const loadHistory = useCallback(
    (page) => api.fetchInviteHistory(tab === 'received' ? 'RECEIVED' : 'SENT', page),
    [tab],
  )
  const pending = usePagedList(loadPending)
  const history = usePagedList(loadHistory)

  const changeTab = (next) => {
    navigate(`/invites/${next}`)
    setInviteError('')
  }

  /** 대기 목록과 이력을 함께 다시 읽는다 */
  const refreshAll = async () => {
    await Promise.all([pending.reload(), history.reload()])
  }

  const handleAccept = async (invite) => {
    setProcessing({ action: 'accept', id: invite.id })
    setInviteError('')
    try {
      await api.acceptInvite(invite.id)
    } catch (err) {
      const limitMessage = errorMessage(err, ACCEPT_ERROR, null)
      setInviteError(limitMessage ?? INVITE_GONE_MESSAGE)
      if (!limitMessage) pending.reload()
      setProcessing(null)
      return
    }
    await refreshAll()
    setProcessing(null)
    navigate(`/groups/${invite.group.id}`)
  }

  const handleReject = async (invite) => {
    setProcessing({ action: 'reject', id: invite.id })
    try {
      await api.rejectInvite(invite.id)
    } catch {
      setInviteError(INVITE_GONE_MESSAGE)
    } finally {
      await refreshAll()
      setProcessing(null)
    }
  }

  const handleRevoke = async (invite) => {
    setProcessing({ action: 'revoke', id: invite.id })
    try {
      await api.revokeInvite(invite.group.id, invite.id)
    } catch {
      setInviteError(INVITE_GONE_MESSAGE)
    } finally {
      await refreshAll()
      setProcessing(null)
    }
  }

  return (
    <>
      <AppHeader receivedCount={tab === 'received' ? (pending.total ?? 0) : undefined} />

      <main className="page">
        <div className="page-inner">
          <Link to="/groups" className="back-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>

          <h1 className="page-title">초대함</h1>

          <div className="invite-tabs" role="tablist" aria-label="초대 목록">
            {[
              ['received', '받은 초대'],
              ['sent', '보낸 초대'],
            ].map(([key, label]) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={tab === key}
                className={`invite-tab${tab === key ? ' active' : ''}`}
                onClick={() => changeTab(key)}
              >
                {label}
              </button>
            ))}
          </div>

          <section className="group-section">
            <h2 className="group-section-title">대기 중</h2>
            {inviteError && <p className="invite-error">{inviteError}</p>}
            <PagedListBody
              list={pending}
              loadingText="불러오는 중이에요…"
              errorText="초대를 불러오지 못했어요."
              emptyText={tab === 'received' ? '받은 초대가 없어요.' : '보낸 초대가 없어요.'}
              noteClassName="invite-note"
              errorClassName="invite-error"
            >
              <ul className="invite-list">
                {pending.items.map((invite) => (
                  <li key={invite.id} className="invite-item">
                    <Link to={`/groups/${invite.group.id}`} className="invite-group-name">
                      {invite.group.name}
                    </Link>
                    <span className="invite-sent-at">
                      {tab === 'received'
                        ? `${invite.invitedBy.name} 님이 초대`
                        : `${invite.invitee.name} 님에게`}{' '}
                      · {formatDate(invite.createdAt)}
                    </span>
                    {tab === 'received' ? (
                      <div className="received-invite-actions">
                        <button
                          type="button"
                          className="btn-primary btn-sm"
                          disabled={busy}
                          onClick={() => handleAccept(invite)}
                        >
                          {isProcessing('accept', invite.id) ? '수락하는 중…' : '수락'}
                        </button>
                        <button
                          type="button"
                          className="btn-danger btn-sm"
                          disabled={busy}
                          onClick={() => setRejecting(invite)}
                        >
                          {isProcessing('reject', invite.id) ? '거절하는 중…' : '거절'}
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="btn-secondary btn-sm"
                        disabled={busy}
                        onClick={() => handleRevoke(invite)}
                      >
                        {isProcessing('revoke', invite.id) ? '취소하는 중…' : '초대 취소'}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </PagedListBody>
          </section>

          <section className="group-section">
            <h2 className="group-section-title">지난 초대</h2>
            <PagedListBody
              list={history}
              loadingText="불러오는 중이에요…"
              errorText="지난 초대를 불러오지 못했어요."
              emptyText="아직 끝난 초대가 없어요."
              noteClassName="invite-note"
              errorClassName="invite-error"
            >
              <ul className="invite-list">
                {history.items.map((item) => (
                  <li key={item.id} className="invite-item">
                    {/* 삭제된 그룹은 링크를 걸지 않는다 */}
                    {item.group.deleted ? (
                      <span className="invite-group-name">{item.group.name}</span>
                    ) : (
                      <Link to={`/groups/${item.group.id}`} className="invite-group-name">
                        {item.group.name}
                      </Link>
                    )}
                    <span className="invite-counterpart">{item.counterpart.name}</span>
                    <span
                      className={`invite-outcome ${
                        item.outcome === 'ACCEPTED'
                          ? 'invite-outcome-accepted'
                          : 'invite-outcome-failed'
                      }`}
                    >
                      {OUTCOME_LABEL[tab][item.outcome]}
                    </span>
                    <span className="invite-sent-at">{formatDate(item.resolvedAt)}</span>
                  </li>
                ))}
              </ul>
            </PagedListBody>
          </section>
        </div>
      </main>

      {rejecting && (
        <ConfirmDialog
          title="초대를 거절할까요?"
          confirmLabel="거절"
          busy={busy}
          onConfirm={() => {
            const invite = rejecting
            setRejecting(null)
            setInviteError('')
            handleReject(invite)
          }}
          onCancel={() => setRejecting(null)}
        >
          &apos;{rejecting.group.name}&apos; 초대가 지난 초대로 넘어가고,{' '}
          <strong>거절한 사실이 보낸 사람에게도 보여요.</strong> 다시 참여하려면 상대가 새로
          초대해야 해요.
        </ConfirmDialog>
      )}
    </>
  )
}
