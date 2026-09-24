import { useEffect, useState } from 'react'
import { fetchGroups } from '../api/groups.js'
import { VISIBILITIES } from '../data/records.js'
import { GlobeIcon, LockIcon, UsersIcon } from './icons.jsx'
import './VisibilitySelect.css'

const ICONS = { lock: LockIcon, users: UsersIcon, globe: GlobeIcon }

/**
 * 공개 범위 선택. 등록 폼과 상세 화면이 같은 컴포넌트를 쓴다.
 *
 * 기본 선택은 언제나 "나만 보기" 이며, 넓히는 것은 사용자가 직접 고른 결과여야 한다.
 * "그룹 공유" 인데 선택된 그룹이 없으면 결과적으로 비공개라는 사실을 그 자리에서 알린다 —
 * 저장을 막지는 않는다. 범위를 먼저 고르고 그룹을 나중에 붙이는 순서가 자연스럽기 때문이다.
 *
 * 공유할 그룹 목록은 이 컴포넌트가 직접 읽는다. "그룹 공유" 를 골랐을 때만 필요하므로 그때
 * 읽고, 여는 화면이 미리 받아 두지 않는다. 불러오는 중·실패를 "그룹 없음" 과 구분해 보여준다 —
 * 공개 범위를 정하는 자리에서 사실과 다른 안내를 하지 않기 위해서다.
 */
export default function VisibilitySelect({
  value,
  onChange,
  selectedGroupIds,
  onChangeGroups,
  onCreateGroupClick,
}) {
  const [groups, setGroups] = useState([])
  // 'idle' | 'loading' | 'ready' | 'error'
  const [groupsStatus, setGroupsStatus] = useState('idle')
  const [retryCount, setRetryCount] = useState(0)
  const wantsGroups = value === 'GROUP'

  // 다른 범위로 갔다가 다시 "그룹 공유" 를 고르면 새로 읽는다. 그 사이 그룹이 바뀌었을 수 있다.
  useEffect(() => {
    if (!wantsGroups) return undefined
    let cancelled = false
    setGroupsStatus('loading')
    fetchGroups()
      .then((result) => {
        if (cancelled) return
        setGroups(result)
        setGroupsStatus('ready')
      })
      .catch(() => {
        if (!cancelled) setGroupsStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [wantsGroups, retryCount])

  const toggleGroup = (groupId) => {
    const next = selectedGroupIds.includes(groupId)
      ? selectedGroupIds.filter((id) => id !== groupId)
      : [...selectedGroupIds, groupId]
    onChangeGroups(next)
  }

  return (
    <fieldset className="visibility-select">
      <legend className="visibility-select-legend">공개 범위</legend>

      <div className="visibility-options">
        {VISIBILITIES.map((option) => {
          const Icon = ICONS[option.icon] ?? LockIcon
          const checked = value === option.key
          return (
            <label
              key={option.key}
              className={`visibility-option${checked ? ' active' : ''}`}
            >
              <input
                type="radio"
                name="visibility"
                value={option.key}
                checked={checked}
                onChange={() => onChange(option.key)}
              />
              <Icon className="visibility-option-icon" />
              <span className="visibility-option-text">
                <span className="visibility-option-label">{option.label}</span>
                <span className="visibility-option-desc">{option.description}</span>
              </span>
            </label>
          )
        })}
      </div>

      {wantsGroups && (
        <div className="visibility-groups">
          {groupsStatus === 'loading' || groupsStatus === 'idle' ? (
            <p className="visibility-groups-empty">그룹을 불러오는 중이에요…</p>
          ) : groupsStatus === 'error' ? (
            <p className="visibility-groups-empty">
              그룹을 불러오지 못했습니다.{' '}
              <button type="button" className="link-btn" onClick={() => setRetryCount((n) => n + 1)}>
                다시 시도
              </button>
            </p>
          ) : groups.length === 0 ? (
            <p className="visibility-groups-empty">
              아직 공유 그룹이 없습니다.{' '}
              <button type="button" className="link-btn" onClick={onCreateGroupClick}>
                그룹 만들기
              </button>
            </p>
          ) : (
            <>
              <ul className="visibility-group-list">
                {groups.map((group) => (
                  <li key={group.id}>
                    <label className="visibility-group-item">
                      <input
                        type="checkbox"
                        checked={selectedGroupIds.includes(group.id)}
                        onChange={() => toggleGroup(group.id)}
                      />
                      <span className="visibility-group-name">{group.name}</span>
                      <span className="visibility-group-count">{group.memberCount}명</span>
                    </label>
                  </li>
                ))}
              </ul>
              {selectedGroupIds.length === 0 && (
                <p className="visibility-groups-warning" role="status">
                  선택한 그룹이 없어 나만 볼 수 있습니다.
                </p>
              )}
            </>
          )}
        </div>
      )}
    </fieldset>
  )
}
