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
 */
export default function VisibilitySelect({
  value,
  onChange,
  groups,
  selectedGroupIds,
  onChangeGroups,
  onCreateGroupClick,
}) {
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

      {value === 'GROUP' && (
        <div className="visibility-groups">
          {groups.length === 0 ? (
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
