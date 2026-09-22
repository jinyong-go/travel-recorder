import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { GROUP_MEMBER_LIMIT } from '../data/groups.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon, PlusIcon } from '../components/icons.jsx'
import './GroupsPage.css'

const formatDate = (iso) => new Date(iso).toLocaleDateString('ko-KR')

const INVITE_ERROR = {
  USER_NOT_FOUND: '해당 이메일로 가입한 사용자가 없습니다. 주소를 다시 확인해주세요.',
  ALREADY_MEMBER: '이미 이 그룹의 멤버입니다.',
}

export default function GroupDetailPage() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const {
    findGroup,
    currentUser,
    pendingInvites,
    sendInvite,
    revokeInvite,
    removeMember,
    leaveGroup,
    deleteGroup,
  } = useRecords()
  const { themeKey, changeTheme } = useTheme()

  const group = findGroup(groupId)
  const [email, setEmail] = useState('')
  const [inviteMessage, setInviteMessage] = useState(null)
  const [confirming, setConfirming] = useState(null)

  if (!group) {
    // 멤버가 아니면 그룹의 존재 자체를 알리지 않는다.
    return (
      <main className="detail-page">
        <div className="detail-missing">
          <h1>그룹을 찾을 수 없습니다</h1>
          <p className="detail-missing-desc">존재하지 않거나 참여하지 않은 그룹이에요.</p>
          <Link to="/groups" className="detail-missing-link">
            <ArrowLeftIcon /> 그룹 목록으로
          </Link>
        </div>
      </main>
    )
  }

  const isOwner = group.ownerId === currentUser.id
  const pending = pendingInvites(group.id)
  const isFull = group.members.length >= GROUP_MEMBER_LIMIT

  const handleSend = (e) => {
    e.preventDefault()
    const trimmed = email.trim()
    if (!trimmed) {
      setInviteMessage({ type: 'error', text: '초대할 상대의 이메일을 입력해주세요.' })
      return
    }

    const result = sendInvite(group.id, trimmed)
    if (result.error) {
      setInviteMessage({
        type: 'error',
        text: INVITE_ERROR[result.error] ?? '초대를 보내지 못했습니다.',
      })
      return
    }

    // 이미 초대한 상대를 다시 초대한 것은 오류가 아니다. 초대가 늘어나지 않을 뿐이다.
    setEmail('')
    setInviteMessage({
      type: 'info',
      text: result.duplicated ? '이미 초대한 상대입니다.' : '초대를 보냈습니다.',
    })
  }

  const confirmAction = () => {
    if (confirming === 'delete') {
      deleteGroup(group.id)
      navigate('/groups')
    } else if (confirming === 'leave') {
      leaveGroup(group.id)
      navigate('/groups')
    } else if (typeof confirming === 'number') {
      removeMember(group.id, confirming)
    }
    setConfirming(null)
  }

  const confirmText = {
    delete: {
      title: '그룹을 삭제할까요?',
      desc: '이 그룹으로만 공유한 기록은 나만 보기 상태가 됩니다. 기록 자체는 삭제되지 않아요.',
      ok: '삭제',
    },
    leave: {
      title: '그룹에서 나갈까요?',
      desc: '이 그룹으로 공유된 기록은 더 이상 보이지 않습니다.',
      ok: '나가기',
    },
    member: {
      title: '멤버를 제외할까요?',
      desc: '제외된 멤버는 이 그룹으로 공유된 기록을 더 이상 볼 수 없습니다.',
      ok: '제외',
    },
  }[typeof confirming === 'number' ? 'member' : confirming] ?? {}

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

          <h1 className="groups-title">{group.name}</h1>
          {group.memo && <p className="group-memo">{group.memo}</p>}
          <p className="groups-desc">
            멤버 {group.members.length}/{GROUP_MEMBER_LIMIT}명
            {isOwner && pending.length > 0 && ` · 대기 중인 초대 ${pending.length}건`} ·{' '}
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
                    {member.id === group.ownerId && <span className="member-owner-tag">소유자</span>}
                  </span>
                  <span className="member-joined">{member.joinedAt} 참여</span>
                  {isOwner && member.id !== group.ownerId && (
                    <button
                      type="button"
                      className="member-remove"
                      onClick={() => setConfirming(member.id)}
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
                정원 판정은 수락 시점에 하므로 정원이 차도 초대를 막지 않는다 (공통 명세 §3.7).
                막지 않는 대신, 보내기 전에 남은 자리와 그 결과를 먼저 알린다 (§5.8.3).
              */}
              <p className="invite-capacity">
                소유자를 포함해 최대 {GROUP_MEMBER_LIMIT}명까지 참여할 수 있어요. 지금{' '}
                {group.members.length}/{GROUP_MEMBER_LIMIT}명
                {pending.length > 0 && ` · 대기 중인 초대 ${pending.length}건`}
                {isFull &&
                  ' — 정원이 차서 지금은 수락되지 않아요. 자리가 나면 같은 초대로 수락할 수 있어요.'}
              </p>
              {/* 자동완성이나 검색 결과를 붙이지 않는다. 가입자를 훑을 수 있는 화면이 된다 (§3.7). */}
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
                <button type="submit" className="btn-primary">
                  <PlusIcon />
                  초대 보내기
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

              {pending.length > 0 && (
                <ul className="invite-list">
                  {pending.map((item) => (
                    <li key={item.id} className="invite-item">
                      <span className="member-avatar" aria-hidden="true">
                        {item.invitee.name.slice(0, 1)}
                      </span>
                      <span className="member-name">{item.invitee.name}</span>
                      <span className="invite-sent-at">{formatDate(item.createdAt)} 보냄</span>
                      <button
                        type="button"
                        className="member-remove"
                        onClick={() => revokeInvite(item.id)}
                      >
                        초대 취소
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}

          <section className="group-section group-danger-zone">
            {isOwner ? (
              <button
                type="button"
                className="detail-delete-btn"
                onClick={() => setConfirming('delete')}
              >
                그룹 삭제
              </button>
            ) : (
              <button
                type="button"
                className="detail-delete-btn"
                onClick={() => setConfirming('leave')}
              >
                그룹 나가기
              </button>
            )}
          </section>
        </div>
      </main>

      {confirming != null && (
        <div className="modal-overlay" onClick={() => setConfirming(null)}>
          <div
            className="modal-panel confirm-panel"
            role="dialog"
            aria-modal="true"
            aria-label={confirmText.title}
            onClick={(e) => e.stopPropagation()}
          >
            <h2>{confirmText.title}</h2>
            <p className="confirm-desc">{confirmText.desc}</p>
            <div className="confirm-actions">
              <button type="button" className="confirm-cancel" onClick={() => setConfirming(null)}>
                취소
              </button>
              <button type="button" className="confirm-ok" onClick={confirmAction}>
                {confirmText.ok}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
