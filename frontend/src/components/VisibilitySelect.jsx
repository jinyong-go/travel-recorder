import { fetchGroups } from '../api/groups.js'
import { VISIBILITIES, effectiveVisibility } from '../data/visibility.js'
import usePagedList from '../hooks/usePagedList.js'
import PagedListBody from './PagedListBody.jsx'
import { VisibilityIcon } from './icons.jsx'
import './VisibilitySelect.css'

/**
 * 공개 범위 선택. 등록 폼과 상세 화면이 같은 컴포넌트를 쓴다.
 *
 * 기본 선택은 언제나 "나만 보기" 이며, 넓히는 것은 사용자가 직접 고른 결과여야 한다.
 * "그룹 공유" 인데 선택된 그룹이 없으면 결과적으로 비공개라는 사실을 그 자리에서 알린다 —
 * 저장을 막지는 않는다. 범위를 먼저 고르고 그룹을 나중에 붙이는 순서가 자연스럽기 때문이다.
 *
 * 공유할 그룹 목록은 이 컴포넌트가 직접 읽는다. "그룹 공유" 를 골랐을 때만 필요하므로 그때
 * 읽고, 여는 화면이 미리 받아 두지 않는다. 목록은 페이지 단위라 "더 보기" 로 이어 붙이며, 고른 그룹은
 * id 로 들고 있어 아직 펼치지 않은 페이지의 그룹도 선택이 유지된다.
 */
export default function VisibilitySelect({
  value,
  onChange,
  selectedGroupIds,
  onChangeGroups,
  onCreateGroupClick,
}) {
  const wantsGroups = value === 'GROUP'
  // 다른 범위로 갔다가 다시 "그룹 공유" 를 고르면 새로 읽는다. 그 사이 그룹이 바뀌었을 수 있다.
  const groups = usePagedList(fetchGroups, wantsGroups)

  const toggleGroup = (groupId) => {
    const next = selectedGroupIds.includes(groupId)
      ? selectedGroupIds.filter((id) => id !== groupId)
      : [...selectedGroupIds, groupId]
    onChangeGroups(next)
  }

  /**
   * "그룹 공유" 아래의 그룹 자리. 불러오는 중·실패를 "그룹 없음" 과 구분해 보여 준다 — 공개 범위를
   * 정하는 자리에서 사실과 다른 안내를 하지 않기 위해서다. 그 판정은 `PagedListBody` 가 한다.
   */
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
