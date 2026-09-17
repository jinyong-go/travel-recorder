// 장소 검색 모달(SPECIFICATION.md 5.2)용 목업 데이터.
// 실제 구현에서는 백엔드가 네이버 지역 검색 오픈 API를 프록시 호출해 집계한 결과로 대체된다.
// 네이버는 장소 고유 ID를 제공하지 않고 백엔드도 자체 ID를 부여하지 않으므로, 검색 결과에 식별자가 없다.
// 선택한 장소는 이름·주소·좌표로만 등록 폼에 전달된다 (5.2 참고).
// 원본 링크(link)는 네이버가 업체 홈페이지가 있을 때만 내려주는 선택 값이라 목업에는 두지 않는다.
// 좌표는 데모용 근사치이며 실제 서비스에서는 백엔드 응답으로 대체된다.
export const PLACE_SEARCH_RESULTS = [
  { name: '제주 흑돼지 본가', category: 'FOOD', region: '제주 제주시', address: '제주특별자치도 제주시 흑돼지거리 12', location: { lat: 33.4996, lng: 126.5312 } },
  { name: '함덕 해수욕장 편집샵', category: 'SHOPPING', region: '제주 조천읍', address: '제주특별자치도 제주시 조천읍 함덕로 45', location: { lat: 33.5434, lng: 126.6692 } },
  { name: '성산일출봉', category: 'SIGHT', region: '제주 성산읍', address: '제주특별자치도 서귀포시 성산읍 성산리 1', location: { lat: 33.4587, lng: 126.9425 } },
  { name: '동문시장 야시장', category: 'FOOD', region: '제주 제주시', address: '제주특별자치도 제주시 관덕로14길 20', location: { lat: 33.5141, lng: 126.5283 } },
  { name: '오설록 티뮤지엄', category: 'SIGHT', region: '제주 서귀포시', address: '제주특별자치도 서귀포시 안덕면 신화역사로 15', location: { lat: 33.3055, lng: 126.2892 } },
  { name: '협재 해변 기념품샵', category: 'SHOPPING', region: '제주 한림읍', address: '제주특별자치도 제주시 한림읍 협재로 105', location: { lat: 33.3939, lng: 126.2397 } },
  { name: '우진해장국', category: 'FOOD', region: '제주 제주시', address: '제주특별자치도 제주시 서사로 11', location: { lat: 33.5121, lng: 126.5219 } },
  { name: '한라산 국립공원', category: 'SIGHT', region: '제주 제주시', address: '제주특별자치도 제주시 1100로 2070-61', location: { lat: 33.3792, lng: 126.4975 } },
  { name: '이호테우 해변', category: 'SIGHT', region: '제주 제주시', address: '제주특별자치도 제주시 이호일동 375-1', location: { lat: 33.5039, lng: 126.4653 } },
  { name: '제주시 로컬 편집샵', category: 'SHOPPING', region: '제주 제주시', address: '제주특별자치도 제주시 탑동로 6', location: { lat: 33.5097, lng: 126.5219 } },
  { name: '올레시장 고등어회', category: 'FOOD', region: '제주 서귀포시', address: '제주특별자치도 서귀포시 중앙로62번길 18', location: { lat: 33.2496, lng: 126.5624 } },
  { name: '카멜리아힐', category: 'SIGHT', region: '제주 안덕면', address: '제주특별자치도 서귀포시 안덕면 병악로 166', location: { lat: 33.2839, lng: 126.3757 } },
  { name: '중문 면세점', category: 'SHOPPING', region: '제주 서귀포시', address: '제주특별자치도 서귀포시 중문관광로 72beon-gil 35', location: { lat: 33.2506, lng: 126.4128 } },
  { name: '흑돼지 명가 칠성점', category: 'FOOD', region: '제주 제주시', address: '제주특별자치도 제주시 칠성로길 22', location: { lat: 33.5133, lng: 126.5253 } },
  { name: '산굼부리', category: 'SIGHT', region: '제주 조천읍', address: '제주특별자치도 제주시 조천읍 비자림로 768', location: { lat: 33.4459, lng: 126.6601 } },
  { name: '애월 카페거리', category: 'SIGHT', region: '제주 애월읍', address: '제주특별자치도 제주시 애월읍 애월로 1', location: { lat: 33.4633, lng: 126.3298 } },
  { name: '누웨마루 거리 상점', category: 'SHOPPING', region: '제주 서귀포시', address: '제주특별자치도 서귀포시 중정로 20', location: { lat: 33.2461, lng: 126.5637 } },
  { name: '돈사돈', category: 'FOOD', region: '제주 제주시', address: '제주특별자치도 제주시 도령로 8', location: { lat: 33.4879, lng: 126.4890 } },
  { name: '만장굴', category: 'SIGHT', region: '제주 구좌읍', address: '제주특별자치도 제주시 구좌읍 만장굴길 182', location: { lat: 33.5286, lng: 126.7714 } },
  { name: '세화 해변 소품샵', category: 'SHOPPING', region: '제주 구좌읍', address: '제주특별자치도 제주시 구좌읍 해맞이해안로 1447', location: { lat: 33.5261, lng: 126.8583 } },
  { name: '제주 흑우 명가', category: 'FOOD', region: '제주 서귀포시', address: '제주특별자치도 서귀포시 태평로 401', location: { lat: 33.2541, lng: 126.5601 } },
  { name: '섭지코지', category: 'SIGHT', region: '제주 성산읍', address: '제주특별자치도 서귀포시 성산읍 섭지코지로 107', location: { lat: 33.4238, lng: 126.9275 } },
  { name: '표선 민속촌 기념품점', category: 'SHOPPING', region: '제주 표선면', address: '제주특별자치도 서귀포시 표선면 민속해안로 631', location: { lat: 33.3255, lng: 126.8330 } },
]
