import { REQUEST_STATUS } from '../api/requestStatus.js'
import { effectiveVisibility } from './visibility.js'

/** 여행 표시용 값과 함수 */

/** 여행 목록의 범위 */
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
  { key: 'START_DATE', label: '여행 시작일 순' },
]

const MS_PER_DAY = 24 * 60 * 60 * 1000

/** 박 수 */
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

/** "2026년 9월" */
export const tripMonthLabel = (trip) => {
  const [year, month] = trip.startDate.split('-')
  return `${year}년 ${Number(month)}월`
}

/** 여행의 공개 범위와 공유 그룹 */
export const tripAudience = (trip) => ({
  visibility: trip.visibility,
  groupIds: (trip.sharedGroups ?? []).map((g) => g.id),
})

/** 여행의 실제 공개 범위 */
export const tripVisibility = (trip) => effectiveVisibility(tripAudience(trip))

/** 예산 표기 */
export const budgetLabel = (budget) =>
  budget == null ? null : `${budget.toLocaleString('ko-KR')}원`

/** 여행을 불러오지 못했을 때의 안내 문구 */
export const TRIP_STATUS_MESSAGE = {
  [REQUEST_STATUS.LOADING]: { title: '여행을 불러오는 중이에요…' },
  [REQUEST_STATUS.ERROR]: { title: '여행을 불러오지 못했어요', desc: '잠시 후 다시 시도해주세요.' },
  [REQUEST_STATUS.MISSING]: { title: '여행을 찾을 수 없어요', desc: '존재하지 않거나 볼 수 없는 여행이에요.' },
}
