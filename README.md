# 여행 지도 (travel-recorder)

다녀온 **여행**을 단위로 방문 장소를 개인 기록으로 남기고, 원하는 상대에게만 선택적으로 공유하는
웹 애플리케이션.

- 사용자가 여행(기간·인원·예산)을 만들고 그 하위에 방문 기록(사진·메모·평점)을 등록
- **공개 범위는 여행 단위**로 지정하며 기본값은 비공개
- 공유 그룹 멤버에게만 공개하거나 전체 공개 가능. 여행을 공유하면 하위 기록 전체가 함께 공개
- 목록은 내 여행·공유받은 여행·둘러보기로 구분, 위치는 네이버 지도로 확인

## 문서 구성

이 저장소는 **명세가 코드보다 우선**함. 서비스가 **무엇인지**는 공통 명세, **어떻게 만드는지**는
각 모듈 명세가 담당.

문서별 담당 범위는 [`AGENTS.md`의 "명세" 절](./AGENTS.md#3-명세), 작업 규칙(승인 절차, 커밋 메시지,
코드·주석 원칙)은 [`AGENTS.md`](./AGENTS.md) 참고.

| 모듈 | 기술 | 문서 |
|---|---|---|
| [`backend/`](./backend) | Kotlin 2.3 / Spring Boot 4.1 / JPA / PostgreSQL + PostGIS / Flyway | [README](./backend/README.md) · [명세](./docs/backend-specification.md) · [API](./docs/api-specification.md) |
| [`frontend/`](./frontend) | React 19 / Vite / react-router | [README](./frontend/README.md) · [명세](./docs/frontend-specification.md) |

모듈 README는 실행 방법·환경 변수·디렉터리 구조, 모듈 명세는 요구사항과 설계 결정을 다룸.

## 빠르게 실행하기

Docker (Compose 2.24 이상) 필요. 저장소 루트에서 한 번에 기동. 프론트엔드는 http://localhost:5173, 백엔드는 8080 포트 사용.
백엔드 CORS에 `http://localhost:5173`이 허용되어 있어 별도 프록시 설정 불필요.

```bash
docker compose up    # PostgreSQL + API 서버(local 프로파일) + 프론트엔드(nginx) + Prometheus·Grafana
```

프론트엔드를 개발 서버(HMR)로 돌리려면 백엔드만 compose로 띄우고 프론트엔드는 따로 기동.
compose의 `web`과 5173 포트가 겹치므로 `web`은 함께 띄우지 않음.

```bash
# 터미널 1 — 백엔드 (PostgreSQL + API 서버)
docker compose up db api

# 터미널 2 — 프론트엔드
cd frontend && npm install && npm run dev
```

- **배치**는 compose에 포함하지 않음. 잡 하나를 돌리고 끝나는 프로그램이라 필요할 때 Gradle로 직접 실행
  ([backend README](./backend/README.md) 참고)
- 네이버 키·지도 Client ID는 루트 `.env`에 둠 (`cp .env.example .env` 후 필요한 줄만 주석 해제).
  지도 Client ID는 번들에 들어가는 값이라 바꾸면 `docker compose up --build`로 다시 빌드해야 함
- **모니터링**: Grafana http://localhost:3000 (admin / `admin`, `.env`의 `GRAFANA_ADMIN_PASSWORD`로 변경),
  Prometheus http://localhost:9090. Prometheus 데이터 소스와 "장소 검색 외부 호출" 대시보드는 자동
  등록됨(`monitoring/grafana/provisioning/dashboards`). 그 밖의 대시보드는 Grafana의
  Dashboards > New > Import에서 ID로 가져옴 — JVM(Micrometer) `4701`, Spring Boot 3.x Statistics `19004`.
  모니터링 없이 앱만 띄우려면 `docker compose up db api web`

- **로그인**: 네이버 OAuth 복구 전까지 `local`·`dev` 프로파일 전용 임시 계정으로 로그인
  (`user1`·`user2`·`user3`, 비밀번호 `password`). 계정이 셋이라 그룹 공유·초대를 직접 주고받기 가능
- **API 키**: 키 없이도 기동 가능. 장소 검색은 키가 없으면 실패 안내, 지도는 "새 창으로 열기" 방식으로 동작.
  발급·설정 방법은 각 모듈 README 참고

| 작업 | 명령 |
|---|---|
| 백엔드 테스트 | `cd backend && ./gradlew test` (Testcontainers, Docker 필요) |
| 프론트엔드 린트 | `cd frontend && npm run lint` |

## 현재 상태

[설계 검토 노트](./docs/reference.md)를 거쳐 모델이 두 번 변경됨.

1. "모두가 공유하는 여행지 + 여러 사용자의 리뷰" → **"개인 소유 방문 기록 + 공개 범위"**
2. 평면적인 방문 기록 목록 → **"여행(Trip) → 여행 기록(TripRecord)" 2계층, 공유 단위도 여행**
   — 이 개정에서 `VisitRecord`를 `TripRecord`로 개명 (API 경로 `/api/records`와 오류 코드는 유지)

두 개정 모두 **명세·백엔드·프론트엔드에 반영 완료**.

- **백엔드**: 여행·여행 기록·공개 범위(여행 단위)·공유 그룹·초대·초대 이력·태그·사진 구현. 공개 범위 판정은
  조회 쿼리 단계에서 수행하며, 기록 조회는 항상 소속 여행을 조인
- **프론트엔드**: 전 화면이 백엔드 API로 동작
- **임시 조치**
    - 네이버 OAuth 대신 임시 인메모리 로그인 사용
    - 시큐리티 인가 정책이 `permitAll()`로 열려 있어, 인증이 필요한 엔드포인트는 컨트롤러가 직접 차단
- **스키마**: Flyway 마이그레이션으로 관리. Flyway 도입 전에 만든 개발용 DB는 재생성 필요
- **다음 단계**: 네이버 OAuth 복구, 시큐리티 인가 정책 복원, 장소 검색 API 연동.
  상세는 [backend-specification.md §6](./docs/backend-specification.md)과
  [frontend-specification.md §10](./docs/frontend-specification.md) 참고
