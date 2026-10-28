/** 기록 카테고리 */
export const CATEGORIES = [
  { key: 'FOOD', label: '맛집' },
  { key: 'SHOPPING', label: '쇼핑' },
  { key: 'SIGHT', label: '관광지' },
]

/** 카테고리 화면 이름 */
export const categoryLabel = (key) => CATEGORIES.find((c) => c.key === key)?.label ?? key

/** 기록을 장소 정보로 바꾼다 */
export const placeOf = (record) => ({
  name: record.name,
  address: record.address,
  roadAddress: record.roadAddress ?? null,
  region: record.address,
  location: { lat: record.latitude, lng: record.longitude },
  link: record.externalLink ?? null,
})

/** 기록 폼 값을 요청 본문으로 바꾼다 */
export const recordPayload = ({ place, category, rating, memo }) => ({
  name: place.name,
  category,
  address: place.address,
  roadAddress: place.roadAddress ?? null,
  externalLink: place.link ?? null,
  latitude: place.location.lat,
  longitude: place.location.lng,
  rating,
  memo,
})
