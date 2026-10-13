/**
 * 공개 범위. 색상만으로 구분하지 않도록 아이콘 이름과 라벨을 함께 둔다.
 * `PRIVATE` 이 언제나 기본값이며, 목록 순서도 좁은 범위 → 넓은 범위다.
 */
export const VISIBILITIES = [
  {
    key: 'PRIVATE',
    label: '나만 보기',
    description: '다른 사람에게는 보이지 않습니다',
    icon: 'lock',
  },
  {
    key: 'GROUP',
    label: '그룹 공유',
    description: '선택한 그룹 멤버가 여행지와 예산을 모두 볼 수 있습니다',
    icon: 'users',
  },
  {
    key: 'PUBLIC',
    label: '전체 공개',
    description: '누구나 로그인 없이 여행지와 예산을 모두 볼 수 있습니다',
    icon: 'globe',
  },
]

export const DEFAULT_VISIBILITY = 'PRIVATE'

/** 공개 범위 표기. 모르는 값은 가장 좁은 "나만 보기" 로 보인다 — 넓게 잘못 보이는 쪽을 피한다. */
export const visibilityMeta = (key) =>
  VISIBILITIES.find((v) => v.key === key) ?? VISIBILITIES[0]


/**
 * 공개 범위 변경이 여행을 볼 수 있는 사람을 새로 늘리는지 판정한다. 각 인자는 `{ visibility, groupIds }` 다.
 *
 * 그룹을 하나도 고르지 않은 "그룹 공유" 는 실제로 나만 보기와 같으므로 그렇게 친다. 그룹 공유끼리는
 * 새로 더해진 그룹이 하나라도 있으면 넓히는 변경이다 — 다른 그룹을 빼더라도 새로 보게 되는 사람이 생긴다.
 */
export const isWideningVisibility = (from, to) => {
  const audience = ({ visibility, groupIds }) =>
    visibility === 'GROUP' && groupIds.length === 0 ? 'PRIVATE' : visibility
  const before = audience(from)
  const after = audience(to)

  if (after === 'PRIVATE' || before === 'PUBLIC') return false
  if (after === 'PUBLIC' || before === 'PRIVATE') return true
  return to.groupIds.some((id) => !from.groupIds.includes(id))
}
