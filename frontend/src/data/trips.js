// 여행 표시용 값과 함수. 여행 데이터 자체는 서버에서 온다 (api/trips.js).
//
// 기간·인원·예산은 여행을 설명하는 값이다. 인원은 함께 간 사람 수일 뿐 공유 대상과 무관하다.

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
