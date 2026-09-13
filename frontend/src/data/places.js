export const CATEGORIES = [
  { key: 'all', label: '전체' },
  { key: 'food', label: '맛집' },
  { key: 'shopping', label: '쇼핑' },
  { key: 'sight', label: '관광지' },
]

const CATEGORY_ICON = {
  food: '🍴',
  shopping: '🛍️',
  sight: '🏞️',
}

export const categoryIcon = (key) => CATEGORY_ICON[key] ?? '📍'

export const categoryLabel = (key) =>
  CATEGORIES.find((c) => c.key === key)?.label ?? key

// 목업 데이터 (백엔드 연동 전까지 화면 구성을 위한 임시 데이터)
export const MOCK_PLACES = [
  { id: 1, name: '제주 흑돼지 본가', category: 'food', region: '제주 제주시', rating: 4.8, distanceKm: 1.2, registeredAt: '2026-09-10', memo: '숙소 근처 흑돼지 맛집, 웨이팅 있음' },
  { id: 2, name: '함덕 해수욕장 편집샵', category: 'shopping', region: '제주 조천읍', rating: 4.2, distanceKm: 3.5, registeredAt: '2026-09-09', memo: '감성 소품샵, 엽서 구매' },
  { id: 3, name: '성산일출봉', category: 'sight', region: '제주 성산읍', rating: 4.9, distanceKm: 12.4, registeredAt: '2026-09-08', memo: '일출 명소, 아침 일찍 방문 추천' },
  { id: 4, name: '동문시장 야시장', category: 'food', region: '제주 제주시', rating: 4.5, distanceKm: 2.1, registeredAt: '2026-09-08', memo: '흑돼지 꼬치, 오메기떡' },
  { id: 5, name: '오설록 티뮤지엄', category: 'sight', region: '제주 서귀포시', rating: 4.6, distanceKm: 18.7, registeredAt: '2026-09-07', memo: '녹차 아이스크림, 티하우스' },
  { id: 6, name: '협재 해변 기념품샵', category: 'shopping', region: '제주 한림읍', rating: 4.0, distanceKm: 22.3, registeredAt: '2026-09-07', memo: '조개 공예품 판매' },
  { id: 7, name: '우진해장국', category: 'food', region: '제주 제주시', rating: 4.7, distanceKm: 1.8, registeredAt: '2026-09-06', memo: '몸국이 유명, 현지인 맛집' },
  { id: 8, name: '한라산 국립공원', category: 'sight', region: '제주 제주시', rating: 4.9, distanceKm: 15.2, registeredAt: '2026-09-05', memo: '어리목 코스, 반나절 소요' },
  { id: 9, name: '이호테우 해변', category: 'sight', region: '제주 제주시', rating: 4.3, distanceKm: 6.9, registeredAt: '2026-09-05', memo: '목마 등대, 노을 명소' },
  { id: 10, name: '제주시 로컬 편집샵', category: 'shopping', region: '제주 제주시', rating: 3.9, distanceKm: 2.5, registeredAt: '2026-09-04', memo: '제주 브랜드 굿즈' },
  { id: 11, name: '올레시장 고등어회', category: 'food', region: '제주 서귀포시', rating: 4.6, distanceKm: 19.8, registeredAt: '2026-09-04', memo: '고등어회, 갈치조림' },
  { id: 12, name: '카멜리아힐', category: 'sight', region: '제주 안덕면', rating: 4.4, distanceKm: 24.1, registeredAt: '2026-09-03', memo: '동백꽃 정원 산책' },
  { id: 13, name: '중문 면세점', category: 'shopping', region: '제주 서귀포시', rating: 4.1, distanceKm: 21.0, registeredAt: '2026-09-02', memo: '화장품, 특산품 쇼핑' },
  { id: 14, name: '흑돼지 명가 칠성점', category: 'food', region: '제주 제주시', rating: 4.4, distanceKm: 1.5, registeredAt: '2026-09-02', memo: '2차로 방문, 육즙 최고' },
  { id: 15, name: '산굼부리', category: 'sight', region: '제주 조천읍', rating: 4.5, distanceKm: 14.6, registeredAt: '2026-09-01', memo: '억새 명소, 가을 추천' },
  { id: 16, name: '애월 카페거리', category: 'sight', region: '제주 애월읍', rating: 4.7, distanceKm: 9.3, registeredAt: '2026-08-31', memo: '오션뷰 카페 다수' },
  { id: 17, name: '누웨마루 거리 상점', category: 'shopping', region: '제주 서귀포시', rating: 3.8, distanceKm: 20.4, registeredAt: '2026-08-31', memo: '야시장, 기념품' },
  { id: 18, name: '돈사돈', category: 'food', region: '제주 제주시', rating: 4.6, distanceKm: 3.0, registeredAt: '2026-08-30', memo: '근고기 맛집, 예약 필수' },
  { id: 19, name: '만장굴', category: 'sight', region: '제주 구좌읍', rating: 4.3, distanceKm: 27.5, registeredAt: '2026-08-29', memo: '용암동굴, 서늘함 주의' },
  { id: 20, name: '세화 해변 소품샵', category: 'shopping', region: '제주 구좌읍', rating: 4.2, distanceKm: 29.1, registeredAt: '2026-08-29', memo: '핸드메이드 액세서리' },
  { id: 21, name: '제주 흑우 명가', category: 'food', region: '제주 서귀포시', rating: 4.5, distanceKm: 17.9, registeredAt: '2026-08-28', memo: '흑우 스테이크' },
  { id: 22, name: '섭지코지', category: 'sight', region: '제주 성산읍', rating: 4.8, distanceKm: 13.0, registeredAt: '2026-08-27', memo: '유채꽃, 드라마 촬영지' },
  { id: 23, name: '표선 민속촌 기념품점', category: 'shopping', region: '제주 표선면', rating: 3.7, distanceKm: 16.2, registeredAt: '2026-08-26', memo: '전통 공예품' },
]
