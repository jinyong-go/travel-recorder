import { REQUEST_STATUS } from '../api/requestStatus.js'
import { effectiveVisibility } from './visibility.js'

/**
 * 여행 표시용 값과 함수. 여행 데이터 자체는 서버에서 온다 (api/trips.js).
 *
 * 기간·인원·예산은 여행을 설명하는 값이다. 인원은 함께 간 사람 수일 뿐 공유 대상과 무관하다.
 */

/**
 * 목록의 세 범위. 성격이 달라 한 목록에 섞지 않고 탭으로 나눈다.
 * 목록에 들어가는 단위는 기록이 아니라 여행이다.
 */
export const SCOPES = [
  { key: 'MINE', label: '내 여행', requiresLogin: true, empty: '아직 여행이 없어요. 다녀온 여행을 만들어 보세요.' },
  {
    key: 'SHARED',
    label: '공유받은 여행',
    requiresLogin: true,
    empty: '공유받은 여행이 없어요. 그룹에 참여하면 여기에 보여요.',
  },
  { key: 'PUBLIC', label: '둘러보기', requiresLogin: false, empty: '공개된 여행이 없어요.' },
]

export const TRIP_SORT_OPTIONS = [
  { key: 'RECENT', label: '최근 만든 순' },
  // 거리순은 없다. 여행은 좌표를 갖지 않는다.
  { key: 'START_DATE', label: '여행 시작일 순' },
]

const MS_PER_DAY = 24 * 60 * 60 * 1000

/** 박·일 수. 같은 날이면 당일치기다. */
export const tripNights = (trip) => {
  const start = new Date(trip.startDate)
  const end = new Date(trip.endDate)
  return Math.max(0, Math.round((end - start) / MS_PER_DAY))
}

/** "당일치기" 또는 "N박 N+1일". */
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

/** "2026년 9월" — 대표 사진이 없는 여행 카드의 자리 표시에 쓴다. */
export const tripMonthLabel = (trip) => {
  const [year, month] = trip.startDate.split('-')
  return `${year}년 ${Number(month)}월`
}

/**
 * 여행 응답의 공개 범위를 `effectiveVisibility`·`isWideningVisibility` 가 받는 모양
 * (`{ visibility, groupIds }`)으로 바꾼다. 공유 그룹은 소유자에게만 내려오므로 없으면 빈 목록이다.
 */
export const tripAudience = (trip) => ({
  visibility: trip.visibility,
  groupIds: (trip.sharedGroups ?? []).map((g) => g.id),
})

/** 여행을 실제로 볼 수 있는 범위. 공개 범위를 화면에 말하는 자리는 저장값이 아니라 이 값을 쓴다. */
export const tripVisibility = (trip) => effectiveVisibility(tripAudience(trip))

/** `1,200,000원` 처럼 쓴다. 예산이 없으면(`null`) 표기도 null 이다 — `0원` 과 구분한다. */
export const budgetLabel = (budget) =>
  budget == null ? null : `${budget.toLocaleString('ko-KR')}원`

/**
 * 여행을 아직 그릴 수 없을 때(불러오는 중·없음·실패) 상세·수정 화면이 보일 문구.
 * 없는 여행과 볼 수 없는 여행을 구분하지 않는다 — 문구가 달라지면 그 차이로 존재가 드러난다.
 */
export const TRIP_STATUS_MESSAGE = {
  [REQUEST_STATUS.LOADING]: { title: '여행을 불러오는 중이에요…' },
  [REQUEST_STATUS.ERROR]: { title: '여행을 불러오지 못했어요', desc: '잠시 후 다시 시도해주세요.' },
  [REQUEST_STATUS.MISSING]: { title: '여행을 찾을 수 없어요', desc: '존재하지 않거나 볼 수 없는 여행이에요.' },
}
