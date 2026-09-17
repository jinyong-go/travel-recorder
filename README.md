# 여행 지도 (travel-recorder)

다녀온 여행 장소를 **개인 기록**으로 남기고, 원하는 상대에게만 선택적으로 공유하는 웹 애플리케이션입니다.
네이버 계정으로 로그인한 사용자가 방문 기록(사진·메모·평점)을 등록하고, 각 기록의 공개 범위를
직접 정합니다. 기본값은 비공개이며, 공유 그룹을 만들어 그 멤버에게만 보여주거나 전체 공개할 수 있습니다.
목록은 내 기록·공유받은 기록·둘러보기로 나뉘고, 위치는 네이버 지도로 확인합니다.

## 구성

서비스 전반의 공통 규칙(도메인 개념, 공개 범위 정책, 모듈 간 연동 계약, 외부 API 제약)은
[공통 요구사항 명세](./SPECIFICATION.md)에 정리되어 있으며, 각 모듈 명세는 이를 구체화합니다.
공개 범위·공유 모델을 어떤 근거로 정했는지는 [설계 검토 노트](./REFERENCE.md)에 있습니다.

| 모듈 | 기술 | 문서 |
|---|---|---|
| [`backend/`](./backend) | Kotlin 2.3 / Spring Boot 4.1 / JPA / PostgreSQL(H2) | [README](./backend/README.md) · [요구사항 명세](./backend/SPECIFICATION.md) |
| [`frontend/`](./frontend) | React 19 / Vite / react-router | [README](./frontend/README.md) · [요구사항 명세](./frontend/SPECIFICATION.md) |

각 모듈의 README는 실행 방법·환경 변수·현재 구현 상태를, SPECIFICATION은 해당 모듈의 요구사항과
설계 결정을 다룹니다. 두 모듈의 규칙이 어긋날 경우 루트 [SPECIFICATION.md](./SPECIFICATION.md)가 우선합니다.

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

[설계 검토 노트](./REFERENCE.md)의 결론에 따라 "모두가 공유하는 여행지 + 여러 사용자의 리뷰"
모델을 **"개인 소유 방문 기록 + 공개 범위"** 모델로 전환했습니다. 명세와 코드 양쪽에 반영되어 있습니다.

- **백엔드**: 방문 기록·공개 범위·공유 그룹·초대 링크가 구현되어 있고, 공개 범위 판정은 조회
  쿼리 단계에서 걸러 냅니다. 다만 시큐리티 인가 정책은 아직 개발 편의를 위해 `permitAll()`로
  열어 둔 상태이며, 인증이 필요한 엔드포인트는 컨트롤러가 직접 막고 있습니다.
- **프론트엔드**: 화면(범위 탭·공개 범위 설정·그룹·초대)이 구현되어 있으나 **아직 백엔드 API를
  호출하지 않고 목업 데이터를 사용**합니다.
- 다음 단계는 두 모듈의 API 연동과 시큐리티 재활성화이며, 남은 작업은
  [backend/SPECIFICATION.md §8](./backend/SPECIFICATION.md)과
  [frontend/SPECIFICATION.md §10](./frontend/SPECIFICATION.md)에 정리되어 있습니다.
