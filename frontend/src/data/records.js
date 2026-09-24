// 카테고리 코드는 API 와 같은 값을 쓰고, 화면 표기만 여기서 매핑한다.
export const CATEGORIES = [
  { key: 'all', label: '전체' },
  { key: 'FOOD', label: '맛집' },
  { key: 'SHOPPING', label: '쇼핑' },
  { key: 'SIGHT', label: '관광지' },
]

const CATEGORY_ICON = {
  FOOD: '🍴',
  SHOPPING: '🛍️',
  SIGHT: '🏞️',
}

/** 기록 메모 최대 길이. 백엔드 요청 검증(`@Size(max = 1000)`)과 같은 값이다. */
export const MEMO_MAX_LENGTH = 1000

export const categoryIcon = (key) => CATEGORY_ICON[key] ?? '📍'

export const categoryLabel = (key) => CATEGORIES.find((c) => c.key === key)?.label ?? key

/**
 * 공개 범위. 색상만으로 구분하지 않도록 아이콘 이름과 라벨을 함께 둔다.
 * `PRIVATE` 이 언제나 기본값이며, 목록 순서도 좁은 범위 → 넓은 범위다.
 */
export const VISIBILITIES = [
  {
    key: 'PRIVATE',
    label: '나만 보기',
    description: '나만 볼 수 있습니다',
    icon: 'lock',
  },
  {
    key: 'GROUP',
    label: '그룹 공유',
    description: '선택한 그룹의 멤버가 볼 수 있습니다',
    icon: 'users',
  },
  {
    key: 'PUBLIC',
    label: '전체 공개',
    description: '링크를 아는 누구나, 로그인하지 않아도 볼 수 있습니다',
    icon: 'globe',
  },
]

export const DEFAULT_VISIBILITY = 'PRIVATE'

export const visibilityMeta = (key) =>
  VISIBILITIES.find((v) => v.key === key) ?? VISIBILITIES[0]

// 목록의 세 범위. 성격이 달라 한 목록에 섞지 않고 탭으로 나눈다 (공통 명세 §2.6).
// 목록에 들어가는 단위는 기록이 아니라 여행이다.
export const SCOPES = [
  { key: 'mine', label: '내 여행', requiresLogin: true, empty: '아직 여행이 없습니다. 다녀온 여행을 만들어보세요.' },
  {
    key: 'shared',
    label: '공유받은 여행',
    requiresLogin: true,
    empty: '공유받은 여행이 없습니다. 그룹에 참여하면 여기에 표시됩니다.',
  },
  { key: 'public', label: '둘러보기', requiresLogin: false, empty: '공개된 여행이 없습니다.' },
]

/**
 * 서버 기록을 지도 버튼·지도 모달·수정 폼의 장소 초안이 쓰는 모양으로 바꾼다.
 *
 * 장소 검색 결과(§5.4)와 같은 모양이라, 검색에서 고른 장소와 기존 기록의 장소를 폼이 구분 없이
 * 다룬다. 기록에는 지역명이 따로 없어 주소를 대신 쓴다.
 */
export const placeOf = (record) => ({
  name: record.name,
  address: record.address,
  roadAddress: record.roadAddress ?? null,
  region: record.address,
  location: { lat: record.latitude, lng: record.longitude },
  link: record.externalLink ?? null,
})

/** 서버 시각(ISO)을 이 브라우저 기준 날짜 `YYYY-MM-DD` 로 쓴다. UTC 로 자르면 새벽 기록의 날짜가 하루 밀린다. */
export const dateLabel = (iso) => {
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
