# 여행 지도 (travel-recorder)

다녀온 여행 장소를 기록하고 공유하는 웹 애플리케이션입니다.
네이버 계정으로 로그인한 사용자가 여행지를 등록하고, 등록된 여행지에 리뷰(별점 + 코멘트)와
사진을 남길 수 있습니다. 목록은 카테고리·별점·거리 기준으로 조회하며, 위치는 네이버 지도로 확인합니다.

## 구성

| 모듈 | 기술 | 문서 |
|---|---|---|
| [`backend/`](./backend) | Kotlin 2.3 / Spring Boot 4.1 / JPA / PostgreSQL(H2) | [README](./backend/README.md) · [요구사항 명세](./backend/SPECIFICATION.md) |
| [`frontend/`](./frontend) | React 19 / Vite / react-router | [README](./frontend/README.md) · [요구사항 명세](./frontend/SPECIFICATION.md) |

각 모듈의 README는 실행 방법·환경 변수·현재 구현 상태를, SPECIFICATION은 요구사항과 설계 결정을 다룹니다.

## 빠르게 실행하기

두 모듈을 각각 띄웁니다. 백엔드는 8080, 프론트엔드는 5173 포트를 사용하며
백엔드 CORS에 `http://localhost:5173`이 이미 허용되어 있어 별도 프록시 설정이 필요 없습니다.

```bash
# 터미널 1 — 백엔드 (local 프로파일, 인메모리 H2)
cd backend && ./gradlew bootRun

# 터미널 2 — 프론트엔드
cd frontend && npm install && npm run dev
```

네이버 로그인과 장소 검색을 쓰려면 API 키가 필요합니다. 키 없이도 서버는 기동되며
목업 데이터로 화면을 확인할 수 있습니다. 발급·설정 방법은 각 모듈 README를 참고하세요.

## 현재 상태

- **백엔드**: 도메인 모델과 API가 구현되어 있습니다. 시큐리티 인가 정책은 개발 편의를 위해
  `permitAll()`로 열어 둔 상태이며, 인증이 필요한 엔드포인트는 컨트롤러에서 직접 막고 있습니다.
- **프론트엔드**: 화면이 구현되어 있으나 **아직 백엔드 API를 호출하지 않고 목업 데이터를 사용**합니다.
- 두 모듈을 잇는 연동 작업이 다음 단계이며, 주의할 지점은
  [backend/SPECIFICATION.md §4.2b](./backend/SPECIFICATION.md)와
  [frontend/SPECIFICATION.md §9](./frontend/SPECIFICATION.md)에 정리되어 있습니다.
