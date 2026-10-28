# backend/AGENTS.md

백엔드에만 해당하는 규칙이다. 공통 규칙은 [루트 AGENTS.md](../AGENTS.md)에 있다.

## 명령

`docker compose` 는 저장소 루트에서, `./gradlew` 는 `backend/` 에서 실행한다.

```bash
docker compose up db              # DB 만 (이어서 bootRun)
docker compose down -v            # 로컬 DB 초기화
./gradlew :external-api:bootRun   # API 서버
./gradlew :batch:bootRun --args='--spring.batch.job.name=<잡 이름>'
./gradlew test                    # 전체 테스트 (Docker 필요)
```

- `bootRun` 은 모듈을 지정해 실행한다.

## 스키마

- 스키마는 Flyway 마이그레이션이 만든다. 이미 적용된 마이그레이션은 고치지 않고 새 버전을 추가한다.
- 엔티티를 고치면 마이그레이션도 함께 추가한다. 어긋나면 서버가 뜨지 않는다.

## 모듈

- `external-api`(API 서버)와 `batch`(정기 작업)는 서로 의존하지 않는다. 테이블을 바꾸면 배치 SQL 도 확인한다.
- 배치 잡은 다시 돌려도 결과가 같게 만든다.

## 패키지

- 도메인으로 먼저 나누고(`auth`, `trip`, `group` …), 그 안을 `presentation` / `service` / `persistence` / `domain`
  으로 나눈다. 상위 없이 존재할 수 없는 하위 도메인은 상위 아래에 둔다 (`trip.record`).
- 의존 방향은 `presentation → service → persistence → domain` 이다.
- 두 도메인 이상이 쓰는 것만 `common` 으로 올린다.

## 계층

본보기는 `TripController` 와 `TripService` 다.

- 컨트롤러는 요청을 서비스 입력으로 바꿔 넘기고 결과를 응답으로 바꾼다. 비즈니스 로직은 두지 않는다.
- 서비스는 요청 DTO 를 받지 않고 도메인 객체를 돌려준다.
- 응답을 만드는 중에 조회하지 않는다. 필요한 값은 서비스가 미리 모아 둔다.

## 인가

- 판정은 조회 쿼리에서 하고, 판정식은 `TripVisibilityPredicates` 하나만 쓴다.
- 비로그인에게 여는 경로는 `SecurityConfig` 에 직접 추가한다.

## 예외·로깅

- 도메인 실패는 `ApiException(ErrorCode.XXX)` 으로 던지고, 응답 변환은 `GlobalExceptionHandler` 가 한다.
- 로그 형식은 `"<대상> <동작> key={}"` 이고, 사람은 내부 id 로만 가리킨다. 개인정보는 남기지 않는다.

## 테스트

- 인가와 공개 범위는 반드시 테스트한다.
- 값 검증은 경계값으로 확인한다.
