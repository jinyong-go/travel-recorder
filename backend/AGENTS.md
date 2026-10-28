# backend/AGENTS.md

## 스택

Kotlin 2.3 · Java 17 · Spring Boot 4.1 · Spring Security 7 · Spring Batch 6 · Hibernate 7 · PostgreSQL(PostGIS) ·
Flyway · Gradle(Kotlin DSL)

- Jackson 은 3 버전이다. `tools.jackson.*` 패키지를 쓴다 (`com.fasterxml.jackson` 아님).
- Boot 3 이하의 예제 코드를 그대로 옮기지 않는다. 패키지와 API 가 바뀐 곳이 많다.

## 명령

`docker compose` 는 저장소 루트에서, `./gradlew` 는 `backend/` 에서 실행한다.

```bash
docker compose up db              # DB 만 (이어서 bootRun)
./gradlew :external-api:bootRun   # API 서버
./gradlew :batch:bootRun --args='--spring.batch.job.name=<잡 이름>'
./gradlew test                    # 전체 테스트 (Docker 필요)
./gradlew :external-api:test --tests '*RecordVisibilityTest'   # 단일 테스트
```

## 구조

- `external-api`(API 서버)와 `batch`(정기 작업)는 서로 의존하지 않는다. 테이블을 바꾸면 배치 SQL 도 확인한다.
- 패키지는 도메인(`auth`, `trip`, `group` …)으로 먼저 나누고, 그 안을 `presentation` / `service` / `persistence` /
  `domain` 으로 나눈다. 의존 방향은 이 순서다. 본보기는 `TripController`·`TripService` 다.
- 컨트롤러는 변환만 하고, 서비스는 요청 DTO 를 받지 않고 도메인 객체를 돌려준다.

## Kotlin·Spring 관례

- 의존성은 생성자 `private val` 로 주입한다.
- 없을 수 있는 값은 nullable 타입(`T?`)으로 돌려준다. `Optional` 을 쓰지 않는다.
- 서비스 클래스에 `@Transactional(readOnly = true)`, 쓰는 메서드에만 `@Transactional` 을 단다.
- 연관은 `FetchType.LAZY` 로 두고, 목록은 필요한 연관을 한 번에 모아 읽는다. 응답을 만드는 중에 조회하지 않는다.
- 설정값은 `@ConfigurationProperties` 로 받는다.

## 하지 않는다

- 엔티티를 컨트롤러·응답으로 내보내지 않는다.
- 이미 적용된 Flyway 마이그레이션을 고치지 않는다. 새 버전을 추가하고, 엔티티를 바꾸면 마이그레이션도 함께 추가한다.
- 공개 범위 판정식을 `TripVisibilityPredicates` 밖에 새로 만들지 않는다. 판정은 조회 쿼리에서 한다.
- 비로그인에게 여는 경로를 `SecurityConfig` 밖에서 열지 않는다.
- 오류 응답을 컨트롤러에서 직접 만들지 않는다. `ApiException(ErrorCode.XXX)` 을 던진다.
- 개인정보(이메일, IP, 좌표)를 로그에 남기지 않는다. 사람은 내부 id 로 가리킨다.

## 테스트

- `@SpringBootTest` + Testcontainers 통합 테스트로 쓴다. 목 라이브러리는 쓰지 않는다.
- 인가와 공개 범위는 반드시 테스트하고, 값 검증은 경계값으로 확인한다.
- 배치 잡은 다시 돌려도 결과가 같아야 한다.
