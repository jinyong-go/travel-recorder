/** 백엔드 관리자 API 가 생기기 전까지 쓰는 고정 목업 데이터 */

const DAY_MS = 24 * 60 * 60 * 1000
const BASE_DATE = Date.UTC(2026, 10, 2)

const toDate = (ms) => new Date(ms).toISOString().slice(0, 10)

/** 최근 14일의 하루 단위 로그인 합계. 오래된 날부터 담는다. */
export const MOCK_LOGIN_DAILY_STATS = Array.from({ length: 14 }, (_, i) => {
  const offset = 13 - i
  // 주말에 조금 많고 하루쯤 0인 날도 있게 만든다.
  const weekday = new Date(BASE_DATE - offset * DAY_MS).getUTCDay()
  const uniqueUserCount = offset === 9 ? 0 : 6 + ((i * 7) % 9) + (weekday % 6 === 0 ? 5 : 0)
  return {
    statDate: toDate(BASE_DATE - offset * DAY_MS),
    loginCount: uniqueUserCount === 0 ? 0 : uniqueUserCount + ((i * 5) % 7),
    uniqueUserCount,
  }
})

const FAMILY_NAMES = ['김', '이', '박', '최', '정', '강', '조', '윤', '장', '임']
const GIVEN_NAMES = ['민준', '서연', '도윤', '하은', '시우', '지유', '예준', '수아', '주원', '지아', '하준']

/** 가입한 사용자. 최근 가입한 사람부터 담는다. */
export const MOCK_USERS = Array.from({ length: 47 }, (_, i) => {
  const id = 47 - i
  const joinedMs = BASE_DATE - (i * 3 + 1) * DAY_MS
  return {
    id,
    name: `${FAMILY_NAMES[id % FAMILY_NAMES.length]}${GIVEN_NAMES[id % GIVEN_NAMES.length]}`,
    email: `user${id}@example.com`,
    provider: 'naver',
    createdAt: new Date(joinedMs).toISOString(),
    // 가입만 하고 다시 오지 않은 사람도 있다.
    lastLoginAt: id % 7 === 0 ? null : new Date(joinedMs + ((id * 13) % (i * 3 + 1)) * DAY_MS).toISOString(),
  }
})
