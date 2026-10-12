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

