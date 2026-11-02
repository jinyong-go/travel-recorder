# 네이버 지도 SDK 연동

프론트엔드가 네이버 지도 SDK 를 불러와 쓰는 데 필요한 사실만 정리한다. 지도 표시 정책은
[공통 명세 §4.3](./specification.md), 지도 모달·설정 메뉴 동작은
[프론트엔드 명세 §5.9](./frontend-specification.md) 가 기준이다.

## 1. 대상

| 항목 | 내용 |
|---|---|
| 제품 | NAVER Maps JavaScript API v3 (NCP Maps · Web Dynamic Map) |
| 발급처 | NCP 콘솔 > Services > Maps > Application 등록 |
| 호출 주체 | 브라우저 |
| 사용 범위 | 지도(`naver.maps.Map`)·마커(`naver.maps.Marker`) |
| 미사용 | 서브모듈 전체 (Geocoder·Panorama·Drawing·Visualization) |

## 2. 로드

- 주소: `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId={Client ID}`
- 키 파라미터: `ncpKeyId`. 예전 `ncpClientId`·`govClientId`·`finClientId` 는 폐지, 새 콘솔에서 발급한 키 필요
- 방식: 지도를 처음 표시할 때 스크립트 동적 삽입, 페이지당 1회 로드
- 완료 판정: 스크립트 `onload` 후 `window.naver.maps` 존재 여부

## 3. 키와 도메인

- 키: 프론트엔드 빌드 환경 변수 `VITE_NAVER_MAP_CLIENT_ID`. 번들에 포함되는 공개 전제 값
- 미설정 시: SDK 를 불러오지 않고 새 창 열기 방식으로 대체
- 도메인 제한: Application 의 Web 서비스 URL 에 등록된 오리진에서만 인증 성공
- 등록 대상: 개발(`http://localhost:5173`)·배포 오리진 전부

## 4. 인증 실패

- 원인: 잘못된 키, 미등록 오리진, 이용 신청하지 않은 서비스
- 시점: 스크립트 로드 이후. `onload` 성공만으로는 인증 성공을 보장하지 않음
- 통지: SDK 가 전역 함수 `window.navermap_authFailure` 호출
- 처리: 지도를 지우고 오류 문구 표시

## 5. 참고

- [Getting Started](https://navermaps.github.io/maps.js.ncp/docs/tutorial-2-Getting-Started.html) — 로드 주소, 키 파라미터 변경, 인증 실패 함수
- [API 레퍼런스](https://navermaps.github.io/maps.js.ncp/docs/) — `naver.maps` 클래스·옵션
- [NCP Maps 개요](https://guide.ncloud-docs.com/docs/maps-overview) — Application 등록, 이용 신청
