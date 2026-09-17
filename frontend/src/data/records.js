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

// 목록의 세 범위. 성격이 달라 한 목록에 섞지 않고 탭으로 나눈다 (공통 명세 6.2).
export const SCOPES = [
  { key: 'mine', label: '내 기록', requiresLogin: true, empty: '아직 기록이 없습니다. 다녀온 곳을 남겨보세요.' },
  {
    key: 'shared',
    label: '공유받은 기록',
    requiresLogin: true,
    empty: '공유받은 기록이 없습니다. 그룹에 참여하면 여기에 표시됩니다.',
  },
  { key: 'public', label: '둘러보기', requiresLogin: false, empty: '공개된 기록이 없습니다.' },
]

// 목업 사용자. 백엔드 연동 시 /api/auth/me 응답으로 대체된다.
export const CURRENT_USER = { id: 1, name: '나', profileImageUrl: null }

export const MOCK_USERS = [
  CURRENT_USER,
  { id: 2, name: '김여행', profileImageUrl: null },
  { id: 3, name: '박기록', profileImageUrl: null },
  { id: 4, name: '이산책', profileImageUrl: null },
]

// 목업 데이터 (백엔드 연동 전까지 화면 구성을 위한 임시 데이터)
//
// 같은 장소를 여러 사람이 각자 기록하는 것이 정상이므로 이름이 겹치는 행을 일부러 남겨 뒀다.
// visibility 가 GROUP 인 행은 sharedGroupIds 로 어느 그룹에 공유됐는지 함께 적는다.
const MOCK_RECORD_ROWS = [
  { id: 1, authorId: 1, name: '제주 흑돼지 본가', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 1.2, createdAt: '2026-09-10', memo: '숙소 근처 흑돼지 맛집, 웨이팅 있음', visibility: 'PRIVATE' },
  { id: 2, authorId: 1, name: '함덕 해수욕장 편집샵', category: 'SHOPPING', region: '제주 조천읍', rating: 4.0, distanceKm: 3.5, createdAt: '2026-09-09', memo: '감성 소품샵, 엽서 구매', visibility: 'GROUP', sharedGroupIds: [1] },
  { id: 3, authorId: 1, name: '성산일출봉', category: 'SIGHT', region: '제주 성산읍', rating: 5, distanceKm: 12.4, createdAt: '2026-09-08', memo: '일출 명소, 아침 일찍 방문 추천', visibility: 'PUBLIC' },
  { id: 4, authorId: 1, name: '동문시장 야시장', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 2.1, createdAt: '2026-09-08', memo: '흑돼지 꼬치, 오메기떡', visibility: 'GROUP', sharedGroupIds: [1, 2] },
  { id: 5, authorId: 1, name: '오설록 티뮤지엄', category: 'SIGHT', region: '제주 서귀포시', rating: 4.5, distanceKm: 18.7, createdAt: '2026-09-07', memo: '녹차 아이스크림, 티하우스', visibility: 'PRIVATE' },
  { id: 6, authorId: 1, name: '협재 해변 기념품샵', category: 'SHOPPING', region: '제주 한림읍', rating: 4.0, distanceKm: 22.3, createdAt: '2026-09-07', memo: '조개 공예품 판매', visibility: 'PRIVATE' },
  { id: 7, authorId: 1, name: '우진해장국', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 1.8, createdAt: '2026-09-06', memo: '몸국이 유명, 현지인 맛집', visibility: 'PUBLIC' },
  { id: 8, authorId: 1, name: '한라산 국립공원', category: 'SIGHT', region: '제주 제주시', rating: 5, distanceKm: 15.2, createdAt: '2026-09-05', memo: '어리목 코스, 반나절 소요', visibility: 'GROUP', sharedGroupIds: [2] },
  { id: 9, authorId: 1, name: '이호테우 해변', category: 'SIGHT', region: '제주 제주시', rating: 4.5, distanceKm: 6.9, createdAt: '2026-09-05', memo: '목마 등대, 노을 명소', visibility: 'PRIVATE' },
  { id: 10, authorId: 1, name: '제주시 로컬 편집샵', category: 'SHOPPING', region: '제주 제주시', rating: 4.0, distanceKm: 2.5, createdAt: '2026-09-04', memo: '제주 브랜드 굿즈', visibility: 'PRIVATE' },
  { id: 11, authorId: 1, name: '올레시장 고등어회', category: 'FOOD', region: '제주 서귀포시', rating: 4.5, distanceKm: 19.8, createdAt: '2026-09-04', memo: '고등어회, 갈치조림', visibility: 'PUBLIC' },
  { id: 12, authorId: 1, name: '카멜리아힐', category: 'SIGHT', region: '제주 안덕면', rating: 4.5, distanceKm: 24.1, createdAt: '2026-09-03', memo: '동백꽃 정원 산책', visibility: 'PRIVATE' },
  { id: 13, authorId: 1, name: '중문 면세점', category: 'SHOPPING', region: '제주 서귀포시', rating: 4.0, distanceKm: 21.0, createdAt: '2026-09-02', memo: '화장품, 특산품 쇼핑', visibility: 'GROUP', sharedGroupIds: [1] },
  { id: 14, authorId: 1, name: '흑돼지 명가 칠성점', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 1.5, createdAt: '2026-09-02', memo: '2차로 방문, 육즙 최고', visibility: 'PRIVATE' },

  // 공유받은 기록 — 내가 멤버인 그룹(2: 제주 동행)으로 공유된 타인의 기록
  { id: 15, authorId: 2, name: '산굼부리', category: 'SIGHT', region: '제주 조천읍', rating: 4.5, distanceKm: 14.6, createdAt: '2026-09-01', memo: '억새 명소, 가을 추천', visibility: 'GROUP', sharedGroupIds: [2] },
  { id: 16, authorId: 2, name: '애월 카페거리', category: 'SIGHT', region: '제주 애월읍', rating: 5, distanceKm: 9.3, createdAt: '2026-08-31', memo: '오션뷰 카페 다수', visibility: 'GROUP', sharedGroupIds: [2] },
  { id: 17, authorId: 3, name: '돈사돈', category: 'FOOD', region: '제주 제주시', rating: 4.5, distanceKm: 3.0, createdAt: '2026-08-30', memo: '근고기 맛집, 예약 필수', visibility: 'GROUP', sharedGroupIds: [2] },

  // 둘러보기 — 타인의 전체 공개 기록
  { id: 18, authorId: 2, name: '만장굴', category: 'SIGHT', region: '제주 구좌읍', rating: 4.0, distanceKm: 27.5, createdAt: '2026-08-29', memo: '용암동굴, 서늘함 주의', visibility: 'PUBLIC' },
  { id: 19, authorId: 3, name: '세화 해변 소품샵', category: 'SHOPPING', region: '제주 구좌읍', rating: 4.0, distanceKm: 29.1, createdAt: '2026-08-29', memo: '핸드메이드 액세서리', visibility: 'PUBLIC' },
  { id: 20, authorId: 4, name: '제주 흑우 명가', category: 'FOOD', region: '제주 서귀포시', rating: 4.5, distanceKm: 17.9, createdAt: '2026-08-28', memo: '흑우 스테이크', visibility: 'PUBLIC' },
  { id: 21, authorId: 4, name: '섭지코지', category: 'SIGHT', region: '제주 성산읍', rating: 5, distanceKm: 13.0, createdAt: '2026-08-27', memo: '유채꽃, 드라마 촬영지', visibility: 'PUBLIC' },
  { id: 22, authorId: 3, name: '표선 민속촌 기념품점', category: 'SHOPPING', region: '제주 표선면', rating: 4.0, distanceKm: 16.2, createdAt: '2026-08-26', memo: '전통 공예품', visibility: 'PUBLIC' },
  // 같은 장소를 다른 사람이 따로 기록한 경우 — 중복이 아니라 별개 기록이다.
  { id: 23, authorId: 2, name: '성산일출봉', category: 'SIGHT', region: '제주 성산읍', rating: 4.5, distanceKm: 12.4, createdAt: '2026-08-25', memo: '흐려서 일출은 못 봤지만 경치는 좋았어요', visibility: 'PUBLIC' },
]

// 좌표·주소는 같은 장소의 검색 결과 목업에서 가져온다. 값을 두 군데 적어 두면 어긋나기 때문이다.
const SEARCH_RESULT_BY_NAME = new Map(PLACE_SEARCH_RESULTS.map((r) => [r.name, r]))

const USER_BY_ID = new Map(MOCK_USERS.map((u) => [u.id, u]))

export const MOCK_RECORDS = MOCK_RECORD_ROWS.map((row) => {
  const found = SEARCH_RESULT_BY_NAME.get(row.name)
  return {
    ...row,
    author: USER_BY_ID.get(row.authorId) ?? MOCK_USERS[0],
    sharedGroupIds: row.sharedGroupIds ?? [],
    photos: [],
    updatedAt: row.createdAt,
    ...(found ? { address: found.address, location: { ...found.location } } : {}),
  }
})
