/** 공개 범위 목록 */
export const VISIBILITIES = [
  {
    key: 'PRIVATE',
    label: '나만 보기',
    description: '나만 볼 수 있어요',
  },
  {
    key: 'GROUP',
    label: '그룹 공유',
    description: '선택한 그룹 멤버가 여행지와 예산을 모두 볼 수 있어요',
  },
  {
    key: 'PUBLIC',
    label: '전체 공개',
    description: '누구나 로그인 없이 여행지와 예산을 모두 볼 수 있어요',
  },
]

export const DEFAULT_VISIBILITY = 'PRIVATE'

/** 공개 범위 표기 */
export const visibilityMeta = (key) =>
  VISIBILITIES.find((v) => v.key === key) ?? VISIBILITIES[0]

/** 여행을 실제로 볼 수 있는 범위 */
export const effectiveVisibility = ({ visibility, groupIds }) =>
  visibility === 'GROUP' && groupIds.length === 0 ? 'PRIVATE' : visibility

/** 공개 범위 변경이 볼 수 있는 사람을 늘리는지 */
export const isWideningVisibility = (from, to) => {
  const before = effectiveVisibility(from)
  const after = effectiveVisibility(to)

  if (after === 'PRIVATE' || before === 'PUBLIC') return false
  if (after === 'PUBLIC' || before === 'PRIVATE') return true
  return to.groupIds.some((id) => !from.groupIds.includes(id))
}
