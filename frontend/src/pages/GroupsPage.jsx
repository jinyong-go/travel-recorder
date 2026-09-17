import { useState } from 'react'
import { Link } from 'react-router-dom'
import { GROUP_MEMBER_LIMIT } from '../data/groups.js'
import { useRecords } from '../context/RecordsContext.jsx'
import useTheme from '../hooks/useTheme.js'
import ThemeSelector from '../components/ThemeSelector.jsx'
import { ArrowLeftIcon, MapPinIcon, PlusIcon } from '../components/icons.jsx'
import './GroupsPage.css'

export default function GroupsPage() {
  const { myGroups, currentUser, createGroup } = useRecords()
  const { themeKey, changeTheme } = useTheme()
  const [name, setName] = useState('')
  const [error, setError] = useState('')

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
          <Link to="/records" className="back-link">
            <ArrowLeftIcon /> 목록으로
          </Link>

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
    </>
  )
}
