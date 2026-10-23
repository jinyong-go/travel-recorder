/** 카테고리 코드는 API 와 같은 값을 쓰고, 화면 표기만 여기서 매핑한다. */
export const CATEGORIES = [
  { key: 'FOOD', label: '맛집' },
  { key: 'SHOPPING', label: '쇼핑' },
  { key: 'SIGHT', label: '관광지' },
]

/** 카테고리 화면 이름. 모르는 값은 코드를 그대로 보인다. */
export const categoryLabel = (key) => CATEGORIES.find((c) => c.key === key)?.label ?? key

/**
 * 서버 기록을 지도 버튼·지도 모달·수정 폼의 장소 초안이 쓰는 모양으로 바꾼다.
 *
 * 장소 검색 결과와 같은 모양이라, 검색에서 고른 장소와 기존 기록의 장소를 폼이 구분 없이
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

/**
 * 기록 폼 값을 등록·수정 요청 본문으로 바꾼다. `placeOf` 의 반대 방향이다.
 *
 * 화면에 보이지 않는 도로명주소·원본 링크도 장소에 있으면 함께 보낸다. 수정은 전체 갱신이라
 * 입력칸이 없는 값(태그)은 부르는 쪽이 기존 값을 덧붙인다.
 */
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
