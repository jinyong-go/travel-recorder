// 여행 목업. 백엔드 연동 시 /api/trips 응답으로 대체된다.
//
// 여행은 기록의 상위 그룹이자 공유의 단위이며, 공개 범위는 여기에만 있다 (공통 명세 §3.2·§3.5).
// 하위 기록은 자기 범위를 갖지 않고 소속 여행의 것을 그대로 따른다.
//
// 기간·인원·예산은 여행을 설명하는 값이다. 인원은 함께 간 사람 수일 뿐 공유 대상과 무관하다.

import { findUserById, MOCK_USERS } from './records.js'

const TRIP_ROWS = [
  // --- 내 여행 ---
  {
    id: 1,
    ownerId: 1,
    name: '제주 3박 4일',
    startDate: '2026-09-05',
    endDate: '2026-09-08',
    headcount: 4,
    budget: 1250000,
    memo: '가족들과 다녀온 첫 제주. 흑돼지와 해변 위주로 돌았고, 마지막 날에는 면세점에 들러 선물을 샀다. 다음에는 우도까지 넘어가 보고 싶다.',
    coverPhotoUrl: null,
    visibility: 'GROUP',
    sharedGroupIds: [1, 2],
    createdAt: '2026-09-09',
  },
  {
    id: 2,
    ownerId: 1,
    name: '혼자 걷는 제주',
    startDate: '2026-09-02',
    endDate: '2026-09-04',
    headcount: 1,
    budget: 420000,
    memo: '아무에게도 알리지 않고 다녀온 혼행.',
    coverPhotoUrl: null,
    visibility: 'PRIVATE',
    sharedGroupIds: [],
    createdAt: '2026-09-04',
  },
  {
    id: 3,
    ownerId: 1,
    name: '제주 맛집 투어',
    startDate: '2026-09-06',
    endDate: '2026-09-06',
    headcount: 2,
    budget: null,
    memo: '당일치기로 유명한 집만 골라 다녔다.',
    coverPhotoUrl: null,
    visibility: 'PUBLIC',
    sharedGroupIds: [],
    createdAt: '2026-09-06',
  },

  // --- 공유받은 여행 — 내가 멤버인 그룹(2: 제주 동행)으로 공유된 타인의 여행 ---
  {
    id: 4,
    ownerId: 2,
    name: '가을 제주 억새길',
    startDate: '2026-08-31',
    endDate: '2026-09-01',
    headcount: 3,
    budget: 680000,
    memo: '억새가 한창일 때 맞춰 다녀왔다.',
    coverPhotoUrl: null,
    visibility: 'GROUP',
    sharedGroupIds: [2],
    createdAt: '2026-09-01',
  },
  {
    id: 5,
    ownerId: 3,
    name: '먹고 걷는 제주',
    startDate: '2026-08-30',
    endDate: '2026-08-30',
    headcount: 2,
    budget: null,
    memo: '근고기 하나 먹으러 간 당일치기.',
    coverPhotoUrl: null,
    visibility: 'GROUP',
    sharedGroupIds: [2],
    createdAt: '2026-08-30',
  },

  // --- 둘러보기 — 타인의 전체 공개 여행 ---
  {
    id: 6,
    ownerId: 2,
    name: '제주 동굴 탐방',
    startDate: '2026-08-25',
    endDate: '2026-08-29',
    headcount: 2,
    budget: 900000,
    memo: '동굴과 오름을 번갈아 다닌 일정. 만장굴은 여름에 가도 서늘하니 겉옷을 챙기는 편이 좋다.',
    coverPhotoUrl: null,
    visibility: 'PUBLIC',
    sharedGroupIds: [],
    createdAt: '2026-08-29',
  },
  {
    id: 7,
    ownerId: 3,
    name: '제주 소품샵 산책',
    startDate: '2026-08-26',
    endDate: '2026-08-29',
    headcount: 1,
    budget: 350000,
    memo: '해변 근처 소품샵만 골라 다녔다.',
    coverPhotoUrl: null,
    visibility: 'PUBLIC',
    sharedGroupIds: [],
    createdAt: '2026-08-29',
  },
  {
    id: 8,
    ownerId: 4,
    name: '제주 미식 여행',
    startDate: '2026-08-27',
    endDate: '2026-08-28',
    headcount: 4,
    budget: 1100000,
    memo: '흑우와 해산물 위주.',
    coverPhotoUrl: null,
    visibility: 'PUBLIC',
    sharedGroupIds: [],
    createdAt: '2026-08-28',
  },
]

// 소유자의 이름·프로필 사진은 열람자에게도 보이는 값이다 (공통 명세 §3.5).
export const MOCK_TRIPS = TRIP_ROWS.map((row) => ({
  ...row,
  owner: findUserById(row.ownerId) ?? MOCK_USERS[0],
}))

export const TRIP_SORT_OPTIONS = [
  { key: 'recent', label: '최근 등록순' },
  // 거리순은 없다. 여행은 좌표를 갖지 않는다 (공통 명세 §3.2).
  { key: 'startDate', label: '시작일순' },
]

/** 박·일 수. 같은 날이면 당일치기다 (공통 명세 §3.2). */
export const tripNights = (trip) => {
  const start = new Date(trip.startDate)
  const end = new Date(trip.endDate)
  return Math.max(0, Math.round((end - start) / 86400000))
}

export const tripDurationLabel = (trip) => {
  const nights = tripNights(trip)
  return nights === 0 ? '당일치기' : `${nights}박 ${nights + 1}일`
}

/** `2026.09.05 – 09.08` — 같은 해·달이면 뒤쪽을 줄여 쓴다. */
export const tripPeriodLabel = (trip) => {
  const [sy, sm, sd] = trip.startDate.split('-')
  const [ey, em, ed] = trip.endDate.split('-')
  if (trip.startDate === trip.endDate) return `${sy}.${sm}.${sd}`
  if (sy === ey && sm === em) return `${sy}.${sm}.${sd} – ${ed}`
  if (sy === ey) return `${sy}.${sm}.${sd} – ${em}.${ed}`
  return `${sy}.${sm}.${sd} – ${ey}.${em}.${ed}`
}

export const budgetLabel = (budget) =>
  budget == null ? null : `${budget.toLocaleString('ko-KR')}원`
