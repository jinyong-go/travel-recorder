import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useRecords } from '../context/RecordsContext.jsx'
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
  const { receivedInvites, sentInvites, inviteHistoryFor, acceptInvite, rejectInvite, revokeInvite } =
    useRecords()
  const navigate = useNavigate()
  const { themeKey, changeTheme } = useTheme()
  const [inviteError, setInviteError] = useState('')
  const [rejecting, setRejecting] = useState(null)

  const history = inviteHistoryFor(tab)
  const pending = tab === 'received' ? receivedInvites : sentInvites

  const changeTab = (next) => {
    navigate(`/invites/${next}`)
    setInviteError('')
  }

  const handleAccept = (inviteId) => {
    const result = acceptInvite(inviteId)
    if (result.error === 'LIMIT') {
      // 초대는 목록에 그대로 남는다. 자리가 나면 같은 초대로 다시 수락할 수 있다 (§3.7).
      setInviteError('그룹 정원(5명)이 가득 찼습니다. 자리가 나면 다시 수락할 수 있습니다.')
      return
    }
    if (result.error) {
      setInviteError('이미 처리되었거나 취소된 초대입니다.')
      return
    }
    navigate(`/groups/${result.groupId}`)
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
            {pending.length === 0 ? (
              <p className="invite-note">
                {tab === 'received' ? '받은 초대가 없어요.' : '보낸 초대가 없어요.'}
              </p>
            ) : (
              <ul className="invite-list">
                {pending.map((invite) => (
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
                          onClick={() => handleAccept(invite.id)}
                        >
                          수락
                        </button>
                        <button
                          type="button"
                          className="invite-reject"
                          onClick={() => setRejecting(invite)}
                        >
                          거절
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="member-remove"
                        onClick={() => revokeInvite(invite.id)}
                      >
                        초대 취소
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="group-section">
            <h2 className="group-section-title">지난 초대</h2>
            {history.length === 0 ? (
              <p className="invite-note">아직 끝난 초대가 없어요.</p>
            ) : (
              <ul className="invite-list">
                {history.map((item) => (
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
                onClick={() => {
                  rejectInvite(rejecting.id)
                  setRejecting(null)
                  setInviteError('')
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
