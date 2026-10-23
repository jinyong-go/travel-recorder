/**
 * 공개 범위. 색상만으로 구분하지 않도록 라벨을 두고, 화면은 아이콘(`VisibilityIcon`)을 함께 그린다.
 * `PRIVATE` 이 언제나 기본값이며, 목록 순서도 좁은 범위 → 넓은 범위다.
 */
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

/** 공개 범위 표기. 모르는 값은 가장 좁은 "나만 보기" 로 보인다 — 넓게 잘못 보이는 쪽을 피한다. */
export const visibilityMeta = (key) =>
  VISIBILITIES.find((v) => v.key === key) ?? VISIBILITIES[0]

/**
 * 실제로 여행을 볼 수 있는 범위. 인자는 `{ visibility, groupIds }` 다.
 *
 * 그룹을 하나도 고르지 않은 "그룹 공유" 는 소유자 말고 아무도 볼 수 없으므로 나만 보기다. 화면이
 * 범위를 말하는 자리(넓힘 판정, 저장 결과, 그룹 이름 표시, 선택 경고)는 모두 이 값으로 판정한다 —
 * 한 곳만 저장값을 그대로 쓰면 같은 여행을 두고 화면끼리 다른 말을 한다.
 */
export const effectiveVisibility = ({ visibility, groupIds }) =>
  visibility === 'GROUP' && groupIds.length === 0 ? 'PRIVATE' : visibility

/**
 * 공개 범위 변경이 여행을 볼 수 있는 사람을 새로 늘리는지 판정한다. 각 인자는 `{ visibility, groupIds }` 다.
 *
 * 실제 범위(`effectiveVisibility`)로 비교한다. 그룹 공유끼리는 새로 더해진 그룹이 하나라도 있으면
 * 넓히는 변경이다 — 다른 그룹을 빼더라도 새로 보게 되는 사람이 생긴다.
 */
export const isWideningVisibility = (from, to) => {
  const before = effectiveVisibility(from)
  const after = effectiveVisibility(to)

  if (after === 'PRIVATE' || before === 'PUBLIC') return false
  if (after === 'PUBLIC' || before === 'PRIVATE') return true
  return to.groupIds.some((id) => !from.groupIds.includes(id))
}

/**
 * 볼 수 있는 사람이 줄어드는 변경(범위 좁힘, 그룹 삭제·탈퇴·제외) 뒤에 덧붙이는 안내.
 *
 * 사진 URL 은 공개 범위와 무관하게 열리므로, 이미 링크를 본 사람은 범위가 좁아진 뒤에도 사진을 열 수 있다.
 * "이제 아무도 못 본다" 고 믿게 두지 않는다.
 */
export const PHOTO_LINK_NOTICE = '이전에 공유된 사진 링크는 계속 열릴 수 있어요.'
