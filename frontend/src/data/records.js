import { PLACE_SEARCH_RESULTS } from './placeSearchResults.js'

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

// 목업 사용자 디렉터리의 1번 항목. 로그인 사용자 자신은 이제 세션에서 오므로(AuthContext)
// 여기 값은 목업 여행·그룹의 소유자를 표시하는 데에만 쓰인다.
//
// email 은 초대 대상을 찾는 데에만 쓰고 화면에 노출하지 않는다 (공통 명세 §3.1).
export const CURRENT_USER = { id: 1, name: '나', profileImageUrl: null, email: 'me@example.com' }

export const MOCK_USERS = [
  CURRENT_USER,
  { id: 2, name: '김여행', profileImageUrl: null, email: 'travel.kim@example.com' },
  { id: 3, name: '박기록', profileImageUrl: null, email: 'record.park@example.com' },
  { id: 4, name: '이산책', profileImageUrl: null, email: 'walk.lee@example.com' },
]

/**
 * 이메일 완전 일치로 사용자를 찾는다. 대소문자는 구분하지 않으며, 없으면 null 이다.
 *
 * 부분 일치 검색을 여는 순간 가입자 목록을 훑을 수 있게 되므로, 초대는 정확한 주소를 아는
 * 상대에게만 보낼 수 있다 (공통 명세 §3.7).
 */
export const findUserByEmail = (email) => {
  const normalized = email.trim().toLowerCase()
  return MOCK_USERS.find((u) => u.email.toLowerCase() === normalized) ?? null
}

export const findUserById = (id) => MOCK_USERS.find((u) => u.id === id) ?? null

// 목업 데이터 (백엔드 연동 전까지 화면 구성을 위한 임시 데이터)
//
// 같은 장소를 여러 사람이 각자 기록하는 것이 정상이므로 이름이 겹치는 행을 일부러 남겨 뒀다.
// **기록에는 공개 범위가 없다.** tripId 로 소속 여행을 가리키고, 볼 수 있는지는 그 여행이
// 정한다 (공통 명세 §3.3). 여행 목록은 trips.js 에 있다.
const MOCK_RECORD_ROWS = [
  { id: 1, tripId: 2, authorId: 1, name: '제주 흑돼지 본가', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 1.2, createdAt: '2026-09-10', memo: '숙소 근처 흑돼지 맛집, 웨이팅 있음' },
  { id: 2, tripId: 1, authorId: 1, name: '함덕 해수욕장 편집샵', category: 'SHOPPING', region: '제주 조천읍', rating: 4.0, distanceKm: 3.5, createdAt: '2026-09-09', memo: '감성 소품샵, 엽서 구매' },
  { id: 3, tripId: 3, authorId: 1, name: '성산일출봉', category: 'SIGHT', region: '제주 성산읍', rating: 5, distanceKm: 12.4, createdAt: '2026-09-08', memo: '일출 명소, 아침 일찍 방문 추천' },
  { id: 4, tripId: 1, authorId: 1, name: '동문시장 야시장', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 2.1, createdAt: '2026-09-08', memo: '흑돼지 꼬치, 오메기떡' },
  { id: 5, tripId: 2, authorId: 1, name: '오설록 티뮤지엄', category: 'SIGHT', region: '제주 서귀포시', rating: 4.5, distanceKm: 18.7, createdAt: '2026-09-07', memo: '녹차 아이스크림, 티하우스' },
  { id: 6, tripId: 2, authorId: 1, name: '협재 해변 기념품샵', category: 'SHOPPING', region: '제주 한림읍', rating: 4.0, distanceKm: 22.3, createdAt: '2026-09-07', memo: '조개 공예품 판매' },
  { id: 7, tripId: 3, authorId: 1, name: '우진해장국', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 1.8, createdAt: '2026-09-06', memo: '몸국이 유명, 현지인 맛집' },
  { id: 8, tripId: 1, authorId: 1, name: '한라산 국립공원', category: 'SIGHT', region: '제주 제주시', rating: 5, distanceKm: 15.2, createdAt: '2026-09-05', memo: '어리목 코스, 반나절 소요' },
  { id: 9, tripId: 2, authorId: 1, name: '이호테우 해변', category: 'SIGHT', region: '제주 제주시', rating: 4.5, distanceKm: 6.9, createdAt: '2026-09-05', memo: '목마 등대, 노을 명소' },
  { id: 10, tripId: 2, authorId: 1, name: '제주시 로컬 편집샵', category: 'SHOPPING', region: '제주 제주시', rating: 4.0, distanceKm: 2.5, createdAt: '2026-09-04', memo: '제주 브랜드 굿즈' },
  { id: 11, tripId: 3, authorId: 1, name: '올레시장 고등어회', category: 'FOOD', region: '제주 서귀포시', rating: 4.5, distanceKm: 19.8, createdAt: '2026-09-04', memo: '고등어회, 갈치조림' },
  { id: 12, tripId: 2, authorId: 1, name: '카멜리아힐', category: 'SIGHT', region: '제주 안덕면', rating: 4.5, distanceKm: 24.1, createdAt: '2026-09-03', memo: '동백꽃 정원 산책' },
  { id: 13, tripId: 1, authorId: 1, name: '중문 면세점', category: 'SHOPPING', region: '제주 서귀포시', rating: 4.0, distanceKm: 21.0, createdAt: '2026-09-02', memo: '화장품, 특산품 쇼핑' },
  { id: 14, tripId: 2, authorId: 1, name: '흑돼지 명가 칠성점', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 1.5, createdAt: '2026-09-02', memo: '2차로 방문, 육즙 최고' },

  // 공유받은 기록 — 내가 멤버인 그룹(2: 제주 동행)으로 공유된 타인의 기록
  { id: 15, tripId: 4, authorId: 2, name: '산굼부리', category: 'SIGHT', region: '제주 조천읍', rating: 4.5, distanceKm: 14.6, createdAt: '2026-09-01', memo: '억새 명소, 가을 추천' },
  { id: 16, tripId: 4, authorId: 2, name: '애월 카페거리', category: 'SIGHT', region: '제주 애월읍', rating: 5, distanceKm: 9.3, createdAt: '2026-08-31', memo: '오션뷰 카페 다수' },
  { id: 17, tripId: 5, authorId: 3, name: '돈사돈', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 3.0, createdAt: '2026-08-30', memo: '근고기 맛집, 예약 필수' },

  // 둘러보기 — 타인의 전체 공개 기록
  { id: 18, tripId: 6, authorId: 2, name: '만장굴', category: 'SIGHT', region: '제주 구좌읍', rating: 4.0, distanceKm: 27.5, createdAt: '2026-08-29', memo: '용암동굴, 서늘함 주의' },
  { id: 19, tripId: 7, authorId: 3, name: '세화 해변 소품샵', category: 'SHOPPING', region: '제주 구좌읍', rating: 4.0, distanceKm: 29.1, createdAt: '2026-08-29', memo: '핸드메이드 액세서리' },
  { id: 20, tripId: 8, authorId: 4, name: '제주 흑우 명가', category: 'FOOD', region: '제주 서귀포시', rating: 4.5, distanceKm: 17.9, createdAt: '2026-08-28', memo: '흑우 스테이크' },
  { id: 21, tripId: 8, authorId: 4, name: '섭지코지', category: 'SIGHT', region: '제주 성산읍', rating: 5, distanceKm: 13.0, createdAt: '2026-08-27', memo: '유채꽃, 드라마 촬영지' },
  { id: 22, tripId: 7, authorId: 3, name: '표선 민속촌 기념품점', category: 'SHOPPING', region: '제주 표선면', rating: 4.0, distanceKm: 16.2, createdAt: '2026-08-26', memo: '전통 공예품' },
  // 같은 장소를 다른 사람이 따로 기록한 경우 — 중복이 아니라 별개 기록이다.
  { id: 23, tripId: 6, authorId: 2, name: '성산일출봉', category: 'SIGHT', region: '제주 성산읍', rating: 4.5, distanceKm: 12.4, createdAt: '2026-08-25', memo: '흐려서 일출은 못 봤지만 경치는 좋았어요' },
]

// 좌표·주소는 같은 장소의 검색 결과 목업에서 가져온다. 값을 두 군데 적어 두면 어긋나기 때문이다.
const SEARCH_RESULT_BY_NAME = new Map(PLACE_SEARCH_RESULTS.map((r) => [r.name, r]))

const USER_BY_ID = new Map(MOCK_USERS.map((u) => [u.id, u]))

export const MOCK_RECORDS = MOCK_RECORD_ROWS.map((row) => {
  const found = SEARCH_RESULT_BY_NAME.get(row.name)
  return {
    ...row,
    author: USER_BY_ID.get(row.authorId) ?? MOCK_USERS[0],
    photos: [],
    updatedAt: row.createdAt,
    ...(found ? { address: found.address, location: { ...found.location } } : {}),
  }
})
