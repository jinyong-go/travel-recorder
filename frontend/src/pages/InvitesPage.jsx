import { useCallback, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client.js'
import * as api from '../api/groups.js'
import { useGroups } from '../context/GroupsContext.jsx'
import usePagedList from '../hooks/usePagedList.js'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon } from '../components/icons.jsx'
import './GroupsPage.css'
import './InvitesPage.css'

const formatDate = (iso) => new Date(iso).toLocaleDateString('ko-KR')

/**
 * 끝난 방식의 표기. 같은 사실을 받은 쪽과 보낸 쪽이 각자의 시점으로 읽으므로 주어가 드러나야 한다
 * (명세 §5.8.4). 예: 내가 거절한 것은 "거절함", 상대가 거절한 것은 "거절됨".
 */
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

export default function InvitesPage({ tab }) {
  const navigate = useNavigate()
  const { themeKey, changeTheme } = useTheme()
  const { reloadGroups } = useGroups()
  const [inviteError, setInviteError] = useState('')
  const [rejecting, setRejecting] = useState(null)
  const [busy, setBusy] = useState(false)

  // 탭마다 담기는 항목과 동작이 달라 목록을 따로 부른다 (명세 §5.8.4).
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

  /** 대기·이력·그룹 목록이 함께 바뀐다. 초대 하나가 끝나면 세 곳을 모두 다시 읽는다. */
  const refreshAll = async () => {
    pending.reload()
    history.reload()
    await reloadGroups()
  }

  const handleAccept = async (invite) => {
    setBusy(true)
    setInviteError('')
    try {
      await api.acceptInvite(invite.id)
    } catch (err) {
      const code = err instanceof ApiError ? err.code : null
      if (code === 'GROUP_MEMBER_LIMIT_EXCEEDED') {
        // 초대는 목록에 그대로 남는다. 자리가 나면 같은 초대로 다시 수락할 수 있다 (§3.7).
        setInviteError('그룹 정원(5명)이 가득 찼습니다. 자리가 나면 다시 수락할 수 있습니다.')
      } else {
        setInviteError('이미 처리되었거나 취소된 초대입니다.')
        pending.reload()
      }
      setBusy(false)
      return
    }
    await refreshAll()
    setBusy(false)
    navigate(`/groups/${invite.group.id}`)
  }

  const handleReject = async (invite) => {
    setBusy(true)
    try {
      await api.rejectInvite(invite.id)
    } catch {
      setInviteError('이미 처리되었거나 취소된 초대입니다.')
    } finally {
      await refreshAll()
      setBusy(false)
    }
  }

  const handleRevoke = async (invite) => {
    setBusy(true)
    try {
      await api.revokeInvite(invite.group.id, invite.id)
    } catch {
      setInviteError('이미 처리되었거나 취소된 초대입니다.')
    } finally {
      await refreshAll()
      setBusy(false)
    }
  }

  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-title app-title-link">
          <MapPinIcon className="app-title-icon" />
          여행 지도 <span className="by-yong">by YONG</span>
        </Link>
        <div className="header-actions">
          <ThemeSelector themeKey={themeKey} onChange={changeTheme} />
        </div>
      </header>

      <main className="groups-page">
        <div className="groups-inner">
          <Link to="/groups" className="back-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>

          <h1 className="groups-title">초대함</h1>

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
            {pending.status === 'loading' && pending.items.length === 0 && (
              <p className="invite-note">불러오는 중이에요…</p>
            )}
            {pending.status === 'error' && (
              <p className="invite-error">
                초대를 불러오지 못했습니다.{' '}
                <button type="button" className="link-button" onClick={pending.reload}>
                  다시 시도
                </button>
              </p>
            )}
            {pending.status === 'ready' && pending.items.length === 0 ? (
              <p className="invite-note">
                {tab === 'received' ? '받은 초대가 없어요.' : '보낸 초대가 없어요.'}
              </p>
            ) : (
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
                          className="btn-primary"
                          disabled={busy}
                          onClick={() => handleAccept(invite)}
                        >
                          수락
                        </button>
                        <button
                          type="button"
                          className="invite-reject"
                          disabled={busy}
                          onClick={() => setRejecting(invite)}
                        >
                          거절
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="member-remove"
                        disabled={busy}
                        onClick={() => handleRevoke(invite)}
                      >
                        초대 취소
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {/* 이력은 지워지지 않아 계정이 오래될수록 길어진다 (명세 §5.8.4). */}
            {pending.hasNext && (
              <button type="button" className="link-button" onClick={pending.loadMore}>
                더 보기
              </button>
            )}
          </section>

          <section className="group-section">
            <h2 className="group-section-title">지난 초대</h2>
            {history.status === 'error' && (
              <p className="invite-error">
                지난 초대를 불러오지 못했습니다.{' '}
                <button type="button" className="link-button" onClick={history.reload}>
                  다시 시도
                </button>
              </p>
            )}
            {history.status === 'ready' && history.items.length === 0 ? (
              <p className="invite-note">아직 끝난 초대가 없어요.</p>
            ) : (
              <ul className="invite-list">
                {history.items.map((item) => (
                  <li key={item.id} className="invite-item">
                    {/* 삭제된 그룹도 표시는 다른 항목과 같고, 갈 곳이 없으므로 링크만 걸지
                        않는다. 삭제 사실은 결과 배지가 말한다 (공통 명세 §3.7). */}
                    {item.group.deleted ? (
                      <span className="invite-group-name">{item.group.name}</span>
                    ) : (
                      <Link to={`/groups/${item.group.id}`} className="invite-group-name">
                        {item.group.name}
                      </Link>
                    )}
                    <span className="invite-counterpart">{item.counterpart.name}</span>
                    {/* 색이 가르는 기준은 "그룹에 들어갔는가" 하나다. 끝난 방식마다 색을
                        주지 않는다 — 문구가 이미 말하는 것을 반복할 뿐이다 (명세 §5.8.4). */}
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
            )}
            {history.hasNext && (
              <button type="button" className="link-button" onClick={history.loadMore}>
                더 보기
              </button>
            )}
          </section>
        </div>
      </main>

      {rejecting && (
        <div className="modal-overlay" onClick={() => setRejecting(null)}>
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label="초대를 거절할까요?"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>초대를 거절할까요?</h2>
            <p className="confirm-desc">
              &apos;{rejecting.group.name}&apos; 초대가 지난 초대로 넘어가고,{' '}
              <strong>거절한 사실이 보낸 사람에게도 보입니다.</strong> 다시 참여하려면 상대가 새로
              초대해야 해요.
            </p>
            <div className="confirm-actions">
              <button type="button" className="confirm-cancel" onClick={() => setRejecting(null)}>
                취소
              </button>
              <button
                type="button"
                className="confirm-ok"
                disabled={busy}
                onClick={() => {
                  const invite = rejecting
                  setRejecting(null)
                  setInviteError('')
                  handleReject(invite)
                }}
              >
                거절
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
