// 상세 화면 목업 데이터. 백엔드 연동 시 GET /api/places/{id} 및 리뷰 API 응답으로 대체된다.

const REGISTRANTS = ['김여행', '박기록', '이산책', '최탐방', '정노을']

// 목업 리뷰 수. 별점이 리뷰의 일부이므로 리뷰 수 = 별점 수다. (SPECIFICATION.md 7장)
export const mockReviewCount = (place) => place.ratingCount ?? ((place.id * 7) % 18) + 5

export const mockRegistrant = (place) => REGISTRANTS[place.id % REGISTRANTS.length]

// 평균 별점에 가까운 점수일수록 많이 받도록 분포를 만든다. (목업 전용 근사치)
export const buildRatingDistribution = (average, count) => {
  const weights = [1, 2, 3, 4, 5].map((score) => 1 / (1 + (score - average) ** 2 * 3))
  const weightSum = weights.reduce((sum, w) => sum + w, 0)
  const raw = weights.map((w) => (w / weightSum) * count)

  // 내림한 뒤 남은 개수를 소수부가 큰 점수부터 채워 합계를 count 에 맞춘다.
  const counts = raw.map(Math.floor)
  let remainder = count - counts.reduce((sum, n) => sum + n, 0)
  const byFraction = raw
    .map((value, index) => ({ index, fraction: value - Math.floor(value) }))
    .sort((a, b) => b.fraction - a.fraction)
  for (let i = 0; remainder > 0; i = (i + 1) % byFraction.length, remainder -= 1) {
    counts[byFraction[i].index] += 1
  }

  return [5, 4, 3, 2, 1].map((score) => ({ score, count: counts[score - 1] }))
}

// 코멘트는 선택 입력이므로 content 가 null 인 리뷰(별점만)도 섞어 둔다.
const REVIEW_POOL = [
  { author: '박기록', score: 4.5, content: '주말에 다녀왔는데 사진보다 실물이 훨씬 좋았어요.' },
  { author: '이산책', score: 4, content: '주차가 조금 불편하지만 그래도 다시 갈 의향 있습니다.' },
  { author: '최탐방', score: 5, content: '평일 오전에 가면 사람이 거의 없어서 여유롭게 둘러볼 수 있어요.' },
  { author: '정노을', score: 4.5, content: null },
  { author: '김여행', score: 3.5, content: '기대보다 규모가 작았어요. 그래도 한 번쯤은 볼만합니다.' },
]

const REVIEW_DATES = ['2026-09-14', '2026-09-12', '2026-09-08', '2026-09-03']

// 여행지마다 0~3건의 리뷰를 결정적으로 뽑아 준다.
export const mockReviews = (place) => {
  const total = place.id % 4
  return Array.from({ length: total }, (_, i) => {
    const seed = (place.id * 3 + i) % REVIEW_POOL.length
    const createdAt = REVIEW_DATES[i % REVIEW_DATES.length]
    return {
      id: `${place.id}-${i}`,
      ...REVIEW_POOL[seed],
      createdAt,
      // createdAt 과 다르면 "수정됨"으로 표시한다.
      updatedAt: createdAt,
    }
  }).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}
