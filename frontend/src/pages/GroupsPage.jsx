import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { GROUP_MEMBER_LIMIT } from '../data/groups.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon, PlusIcon } from '../components/icons.jsx'
import './GroupsPage.css'

const formatDate = (iso) => new Date(iso).toLocaleDateString('ko-KR')

export default function GroupsPage() {
  const { myGroups, currentUser, createGroup, receivedInvites, acceptInvite, rejectInvite } =
    useRecords()
  const navigate = useNavigate()
  const { themeKey, changeTheme } = useTheme()
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [inviteError, setInviteError] = useState('')
  const [rejecting, setRejecting] = useState(null)

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

  const handleCreate = (e) => {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setError('그룹 이름을 입력해주세요.')
      return
    }
    if (trimmed.length > 30) {
      setError('그룹 이름은 30자 이하로 입력해주세요.')
      return
    }
    createGroup(trimmed)
    setName('')
    setError('')
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
          <Link to="/trips" className="back-link">
            <ArrowLeftIcon /> 여행 목록으로
          </Link>

          {receivedInvites.length > 0 && (
            <section className="received-invites">
              <h2 className="group-section-title">받은 초대</h2>
              {inviteError && <p className="invite-error">{inviteError}</p>}
              <ul className="invite-list">
                {receivedInvites.map((invite) => (
                  <li key={invite.id} className="invite-item">
                    <span className="member-name">{invite.group.name}</span>
                    <span className="invite-sent-at">
                      {invite.invitedBy.name} 님이 초대 · {formatDate(invite.createdAt)}
                    </span>
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
                        className="member-remove"
                        onClick={() => setRejecting(invite)}
                      >
                        거절
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <h1 className="groups-title">공유 그룹</h1>
          <p className="groups-desc">
            그룹은 기록을 보여줄 대상 목록이에요. 멤버는 공유한 기록만 볼 수 있고,
            수정하거나 삭제할 수는 없습니다.
          </p>

          <form className="group-create-form" onSubmit={handleCreate}>
            <label className="sr-only" htmlFor="group-name">
              그룹 이름
            </label>
            <input
              id="group-name"
              type="text"
              value={name}
              maxLength={30}
              placeholder="예: 가족, 제주 동행"
              onChange={(e) => setName(e.target.value)}
            />
            <button type="submit" className="btn-primary">
              <PlusIcon />
              그룹 만들기
            </button>
          </form>
          {error && <p className="field-error">{error}</p>}

          {myGroups.length === 0 ? (
            <div className="empty-state">
              공유 그룹을 만들면 특정 기록을 원하는 사람에게만 보여줄 수 있습니다.
            </div>
          ) : (
            <ul className="group-list">
              {myGroups.map((group) => {
                const isOwner = group.ownerId === currentUser.id
                return (
                  <li key={group.id} className="group-card">
                    <Link to={`/groups/${group.id}`} className="group-card-link">
                      <span className="group-card-name">{group.name}</span>
                      <span className={`group-role-badge${isOwner ? ' owner' : ''}`}>
                        {isOwner ? '소유자' : '멤버'}
                      </span>
                      <span className="group-card-count">
                        {group.members.length}/{GROUP_MEMBER_LIMIT}명
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
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
              &apos;{rejecting.group.name}&apos; 초대가 목록에서 사라집니다. 다시 참여하려면
              상대가 새로 초대해야 해요.
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
