import { useState } from 'react'
import { Link } from 'react-router-dom'
import { GROUP_MEMBER_LIMIT } from '../data/groups.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon, PlusIcon } from '../components/icons.jsx'
import './GroupsPage.css'

export default function GroupsPage() {
  const { myGroups, currentUser, createGroup, receivedInvites } = useRecords()
  const { themeKey, changeTheme } = useTheme()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [memo, setMemo] = useState('')
  const [error, setError] = useState('')

  const closeCreate = () => {
    setCreating(false)
    setName('')
    setMemo('')
    setError('')
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
    createGroup(trimmed, memo)
    closeCreate()
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

          {/* 같은 목록을 두 화면이 각자 그리지 않도록 여기서는 건수만 알린다 (명세 §5.8.4). */}
          {receivedInvites.length > 0 && (
            <Link to="/invites/received" className="invite-notice">
              받은 초대 {receivedInvites.length}건 <span aria-hidden="true">→</span>
            </Link>
          )}

          <h1 className="groups-title">공유 그룹</h1>
          <p className="groups-desc">
            그룹은 기록을 보여줄 대상 목록이에요. 멤버는 공유한 기록만 볼 수 있고,
            수정하거나 삭제할 수는 없습니다.
          </p>

          <div className="groups-actions">
            <button type="button" className="btn-primary" onClick={() => setCreating(true)}>
              <PlusIcon />
              그룹 만들기
            </button>
          </div>

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
                      {group.memo && <span className="group-card-memo">{group.memo}</span>}
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </main>

      {creating && (
        <div className="modal-overlay" onClick={closeCreate}>
          <div
            className="modal-panel group-form-panel"
            role="dialog"
            aria-modal="true"
            aria-label="새 그룹 만들기"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>새 그룹 만들기</h2>
            <form className="group-form" onSubmit={handleCreate}>
              <label htmlFor="group-name">이름</label>
              <input
                id="group-name"
                type="text"
                value={name}
                maxLength={30}
                autoFocus
                placeholder="예: 가족, 제주 동행"
                onChange={(e) => setName(e.target.value)}
              />

              <label htmlFor="group-memo">
                메모 <span className="field-optional">(선택)</span>
              </label>
              <textarea
                id="group-memo"
                value={memo}
                maxLength={200}
                rows={3}
                placeholder="예: 설 연휴 사진 공유용"
                onChange={(e) => setMemo(e.target.value)}
              />
              <p className="field-hint">멤버에게도 보입니다.</p>

              {error && <p className="field-error">{error}</p>}

              <div className="confirm-actions">
                <button type="button" className="confirm-cancel" onClick={closeCreate}>
                  취소
                </button>
                <button type="submit" className="confirm-ok confirm-ok-safe">
                  만들기
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </>
  )
}
