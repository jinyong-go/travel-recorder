# backend/AGENTS.md

백엔드 모듈 규칙이다. 공통 규칙은 [루트 AGENTS.md](../AGENTS.md), 요구사항은 [`SPECIFICATION.md`](./SPECIFICATION.md)를 따른다.

## 명령

`docker compose` 는 저장소 루트에서, `./gradlew` 는 `backend/` 에서 실행한다.

```bash
docker compose up db              # DB 만 (이어서 bootRun)
docker compose down -v            # 로컬 DB 초기화 (볼륨 삭제)
./gradlew :external-api:bootRun   # API 서버 (기본 local 프로파일)
./gradlew :batch:bootRun --args='--spring.batch.job.name=<잡 이름>'
./gradlew test                    # 모든 모듈 테스트 (Testcontainers — Docker 필요)
./gradlew :external-api:test --tests '*RecordVisibilityTest'   # 단일 테스트
```

- 모듈을 지정하지 않은 `./gradlew bootRun` 은 쓰지 않는다. API 서버가 프로세스를 붙잡아 배치가 돌지 않는다.
- 관리 포트(8081, `/actuator/health`)는 bootRun 일 때만 호스트에서 열린다.

## 스키마

- 스키마는 Flyway 마이그레이션(`external-api/src/main/resources/db/migration`)이 만든다.
  **이미 적용된 마이그레이션은 고치지 않고 다음 버전을 추가한다.**
- 모든 프로파일이 `ddl-auto: validate` 다. 엔티티를 고치면 마이그레이션도 함께 추가한다 — 어긋나면 기동하지 않는다.
- `prod` 는 Flyway 가 꺼져 있다 (배포 절차에서 CLI 로 적용).
- local 시드(`db/seed/local/R__local_seed.sql`)는 반복 마이그레이션이라 몇 번 돌아도 결과가 같게 쓴다.

## 모듈

- `external-api`(API 서버)와 `batch`(정기 작업)는 서로 의존하지 않는다. 배치는 엔티티 없이 SQL 로 다루므로
  테이블을 바꾸면 배치 SQL 도 확인한다.
- 공통 빌드 설정은 루트 `build.gradle.kts` 의 `subprojects` 에 있다. 모듈 빌드 파일에는 그 모듈 의존성만 둔다.
- 배치는 메타데이터 테이블 없이 돈다(`ResourcelessJobRepository`). 재시작 정보가 없으므로 잡은 멱등하게 만든다.
- 잡이 둘 이상이라 `--spring.batch.job.name` 을 반드시 넘긴다. 잡 목록은 [README](./README.md).
- `@SpringBatchTest` 클래스에 `JobExecution`·`StepExecution` 을 돌려주는 메서드를 두지 않는다 — 스코프 팩토리로
  오인되어 매 테스트 전에 호출된다. 테스트마다 `JobOperatorTestUtils.setJob` 으로 잡을 지정한다.

이하 규칙은 `external-api` 기준이다.

## 패키지

- 도메인으로 먼저 나누고(`auth`, `trip`, `group` …), 그 안을 `presentation` / `service` / `persistence` / `domain`
  으로 나눈다. 상위 없이 존재할 수 없는 하위 도메인은 상위 아래에 둔다 (`trip.record`).
- 4계층 밖에는 보조 패키지만 둔다: `config`(설정), `security`(인증 주체), `client`(외부 API 호출),
  `storage`(사진 바이너리 저장), `web`·`util`(`common` 의 공용 도구). `client`·`storage` 는 서비스만 부른다.
- 의존 방향은 `presentation → service → persistence → domain` 이다. `domain` 은 아무것도 모른다.
  - `presentation` 은 엔티티를 모른다. 서비스·persistence 는 요청 DTO·`PageResponse` 를 모른다.
- `persistence`: JPA 엔티티(`*Entity`)·리포지토리·`*Specifications`.
  `domain`: 저장 수단을 모르는 개념 — enum, 도메인 객체, `*Command`, `*Query`.
  외부 API 응답 모델은 그 API 를 부르는 `client` 에 둔다.
- `presentation` 은 하위 패키지 없이 `*Controller`, `*Requests`, `*Responses` 파일로 나눈다.
  DTO 가 하나뿐이면 단수(`*Response`)로 쓴다.
- 두 도메인 이상이 쓰는 것만 `common` 으로 올린다.
- import 는 명시적으로 쓴다. 와일드카드를 쓰지 않는다.

## 계층

본보기는 `trip/presentation/TripController.kt` 와 `trip/service/TripService.kt` 다.

- 컨트롤러: `requireLogin` → 요청 DTO `toCommand()` → 서비스 호출 → `XxxResponse.from(도메인 객체[, userId])`.
  비즈니스 로직과 조회를 두지 않는다. 요청 형식의 검증(파라미터 조합, 필수 좌표 등)은 컨트롤러가 해도 된다.
- 서비스는 요청 DTO 를 받지 않는다. 필드가 많으면 `*Command`, 한두 개면 파라미터로 받는다.
  Bean Validation 은 요청 DTO 에만 건다. 업로드 파일(`MultipartFile`)은 그대로 넘긴다.
- 서비스는 DTO 가 아니라 도메인 객체를 반환한다. 예외로, 다른 서비스가 연관을 걸 때 쓰는 메서드
  (`findOwnedTrip`, `findEditableRecord` 등)는 엔티티를 반환한다.
- 서비스 클래스에 `@Transactional(readOnly = true)`, 쓰는 메서드에만 `@Transactional` 을 단다.
  예외: DB 를 쓰지 않는 서비스는 달지 않고, 호출자 트랜잭션 안에서만 돌아야 하는 서비스는
  `Propagation.MANDATORY` 로 그 의도를 드러낸다.
- 응답 DTO 는 `companion object` 의 `from()` 으로 만든다. 도메인 객체에 `toXxxResponse()` 확장 함수를 두지 않는다.
- **응답 변환 중에 조회하지 않는다** (N+1). 필요한 것은 서비스가 미리 모아 담는다 (`findByTripIdIn` 류).
- 요청자에 따라 달라지는 값(`isOwner` 등)은 도메인 객체 필드가 아니라 변환 시점에 정한다.
- 소유자에게만 나가는 값은 응답에서 가리지 말고 소유자 조회일 때만 읽는다 (`TripService.sharesOf`).
- 목록 서비스는 `page: Int` 를 받아 `PageResult` 를 돌려준다. `Page`·`Pageable` 은 서비스–리포지토리
  사이에서만 쓴다. 정렬은 조회 쿼리에서 끝낸다.

## 인가 — 가장 조심할 자리

빠뜨리는 순간 곧바로 정보 유출이다. 판정식은 명세 §2.2 를 따른다.

- **판정은 조회 쿼리 단계에서 한다.** 판정식은 `TripVisibilityPredicates` 하나뿐이고, 새 조회 경로도 이것을
  쓴다. 메모리 판정을 두지 않는다. 단건은 `findViewable*`·`findEditable*` 를 거친다.
- 기록 조회는 항상 소속 여행을 조인한다. 조인 없는 경로가 곧 우회로다.
- 열람 권한이 없으면 `403` 이 아니라 `404` 이고, 없는 것과 구분되지 않아야 한다. 여행을 못 봐서 가려지는
  기록도 `RECORD_NOT_FOUND` 다.
- 그룹은 예외로 비멤버 `403`, 없는 그룹 `404` 다. 단 여행 공유 요청에 담긴 접근 불가 그룹 id 는 `404` 다.
- `SecurityConfig` 가 `permitAll()` 이므로 새 엔드포인트에도 `requireLogin(principal)` 을 빠뜨리지 않는다.

## 예외

- 도메인 실패는 `throw ApiException(ErrorCode.XXX)`. 컨트롤러가 오류 상태 코드를 직접 만들지 않는다
  (성공 응답의 201·204 는 `ResponseEntity` 로 만든다).
- 새 `ErrorCode` 는 명세의 오류 표에도 함께 적는다.
- 변환은 `GlobalExceptionHandler` 하나가 한다. 핸들러를 새로 만들지 않는다.
- `500`·`409` 메시지는 고정 문구다. 예외 메시지(테이블·제약 이름)는 로그로만 남긴다.

## 로깅

- `private val log = LoggerFactory.getLogger(javaClass)`.
- 형식: `"<대상> <동작>[: <사유>] key={} key={}"` — `"초대 수락 거부: 정원 초과 inviteId={} groupId={}"`.
  키는 영문 camelCase, 단위는 키 이름에(`totalBytes`), 상태 변화는 `from={} to={}`. 괄호 부연을 붙이지 않는다.
  예외: `LoggingFilter` 의 요청 한 줄(`GET /api/trips -> 200 (12ms)`)은 액세스 로그 관례를 따른다.
- 변경 로그는 저장 후에, 차단 로그는 예외를 던지기 직전에 찍는다.
- 레벨: 도메인 이벤트·권한 차단 `DEBUG`, 요청 한 줄·배치 결과 `INFO`, 외부 API 실패·제약 위반 `WARN`,
  처리되지 않은 예외 `ERROR`.
- 이메일·IP·User-Agent·기준 좌표·원본 파일명은 남기지 않는다. 사람은 내부 id 로만 가리킨다.

## 영속성

- soft delete 는 `deletedAt` 하나로 판단하고, 조회 제외는 `@SQLRestriction` 이 한다. 네이티브 SQL 은
  `deleted_at IS NULL` 을 직접 적어야 부분 인덱스를 탄다.
- 수정 응답에 `updatedAt` 을 담으면 `saveAndFlush` 를 쓴다 — `@PreUpdate` 가 flush 시점에 돈다.
- 평점 단위와 여행 기간 역전은 Bean Validation 과 DB `CHECK` 양쪽에서 막는다.
- 동적 조회 조건은 `XxxSpecifications` 에 모은다.
- 거리 정렬은 `PostgisFunctions.knn_distance` 로 하며, 식이 위치 인덱스 표현식과 같아야 한다.

## 테스트

- `external-api/src/test/kotlin/...`, `batch/src/test/...`, 파일명 `XxxTest.kt`. JUnit 5.
- **인가와 공개 범위는 반드시 테스트한다.** 기준은 `RecordVisibilityTest` — 비공개 접근 404, 그룹 탈퇴·삭제 시
  즉시 차단, `scope` 로 권한이 넓어지지 않는지.
- 값 검증은 경계값으로 확인한다.
