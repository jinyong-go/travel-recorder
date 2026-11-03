import { fetchGroups } from '../api/groups.js'
import { VISIBILITIES, effectiveVisibility } from '../data/visibility.js'
import usePagedList from '../hooks/usePagedList.js'
import PagedListBody from './PagedListBody.jsx'
import { VisibilityIcon } from './icons.jsx'
import './VisibilitySelect.css'

/** 공개 범위 선택 */
export default function VisibilitySelect({
  value,
  onChange,
  selectedGroupIds,
  onChangeGroups,
  onCreateGroupClick,
}) {
  const wantsGroups = value === 'GROUP'
  const groups = usePagedList(fetchGroups, wantsGroups)

  const toggleGroup = (groupId) => {
    const next = selectedGroupIds.includes(groupId)
      ? selectedGroupIds.filter((id) => id !== groupId)
      : [...selectedGroupIds, groupId]
    onChangeGroups(next)
  }

  /** 공유할 그룹 목록 */
  const renderGroups = () => (
    <PagedListBody
      list={groups}
      loadingText="그룹을 불러오는 중이에요…"
      errorText="그룹을 불러오지 못했어요."
      emptyText={
        <>
          아직 공유 그룹이 없어요.{' '}
          <button type="button" className="link-button" onClick={onCreateGroupClick}>
            그룹 만들기
          </button>
        </>
      }
      noteClassName="visibility-groups-empty"
      errorClassName="visibility-groups-empty"
    >
      <>
        <ul className="visibility-group-list">
          {groups.items.map((group) => (
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
        {effectiveVisibility({ visibility: value, groupIds: selectedGroupIds }) === 'PRIVATE' && (
          <p className="visibility-groups-warning" role="status">
            선택한 그룹이 없어 나만 볼 수 있어요.
          </p>
        )}
      </>
    </PagedListBody>
  )

  return (
    <fieldset className="visibility-select">
      <legend className="visibility-select-legend">공개 범위</legend>

      <div className="visibility-options">
        {VISIBILITIES.map((option) => {
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
              <VisibilityIcon visibility={option.key} className="visibility-option-icon" />
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
          {renderGroups()}
        </div>
      )}
    </fieldset>
  )
}
