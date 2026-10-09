# backend/CLAUDE.md

백엔드 모듈에서 작업할 때의 규칙이다. 승인 절차·명세 작업·코드 원칙·주석 규칙은
[루트 CLAUDE.md](../CLAUDE.md)에 있고 **여기서 반복하지 않는다.**

요구사항은 [`SPECIFICATION.md`](./SPECIFICATION.md)를 따른다. 명세에 없는 결정이 필요하면
코드로 정하지 말고 묻는다.

## 명령

```bash
./gradlew :external-api:bootRun   # API 서버 실행 (기본 local 프로파일, H2 인메모리)
./gradlew :batch:bootRun          # 배치 실행 (잡을 돌리고 종료)
./gradlew test                    # 모든 모듈 테스트
./gradlew build                   # 빌드 (테스트 포함)
```

- **모듈을 지정하지 않은 `./gradlew bootRun` 은 쓰지 않는다.** 두 모듈의 `bootRun` 이 함께 잡히고,
  API 서버가 프로세스를 붙잡아 배치는 돌지 않는다.

- 프로파일: `local`(H2, `MODE=PostgreSQL`) / `dev` / `prod`(PostgreSQL)
- **세 프로파일 모두 `ddl-auto: validate`** 다. 엔티티와 `schema.sql` 이 어긋나면 기동조차 되지
  않으므로, **엔티티를 고치면 `schema.sql` 도 같이 고친다.**

## 모듈

| 모듈 | 하는 일 | 의존성 |
|---|---|---|
| `external-api` | 프론트엔드가 부르는 API 서버 | Web MVC, Data JPA, Security, Validation |
| `batch` | 정기 정리 작업. 웹 서버 없이 잡을 돌리고 종료 | Spring Batch, JDBC |

- 공통 빌드 설정(플러그인 버전·Kotlin 옵션·Java 17·JUnit)은 루트 `build.gradle.kts` 의
  `subprojects` 에 있다. 모듈 `build.gradle.kts` 에는 그 모듈의 의존성만 둔다.
- **두 모듈은 서로 의존하지 않는다.** 배치가 API 모듈의 엔티티를 가져다 쓰지 않는다.
- **배치는 메타데이터 테이블(`BATCH_*`) 없이 돈다** (`ResourcelessJobRepository`). 잡은 몇 번을
  돌려도 결과가 같게(멱등) 만든다 — 재시작 정보가 남지 않으므로 실패하면 처음부터 다시 돌린다.
  재시작이 필요한 잡이 생기면 JDBC 저장소와 그 테이블의 관리 방식을 함께 정한다.
- **배치는 엔티티 없이 SQL 로 다룬다.** 스키마의 기준은 `external-api` 의 `schema.sql` 하나이며,
  배치는 빌드 시 이 파일을 리소스로 받아 local·test DB 를 만든다. 테이블을 바꾸면 배치의 SQL 도
  함께 확인한다.
- 잡 목록: `loginHistoryCleanupJob` — 보관 기간이 지난 로그인 이력 삭제 (명세 §3.2).
- 아래 패키지 구조·계층 책임·인가·영속성 규칙은 `external-api` 기준이다.

## 스택

Kotlin 2.3 / Spring Boot 4.1 / Spring Data JPA / Spring Security OAuth2 Client / Bean Validation / Spring Batch 6

## 패키지 구조

```
com.yong.travel
  ├─ auth     인증·사용자 (config, presentation, service, domain, persistence, security)
  ├─ trip     여행 — 공개 범위와 공유의 단위
  │   └─ record   여행 기록 (여행 없이 존재하지 않는다)
  ├─ group    공유 그룹·초대
  ├─ photo    사진 (storage 하위에 저장소 구현체)
  ├─ search   장소 검색 (client 하위에 외부 API 호출)
  ├─ tag      태그
  └─ common   config · domain(PageResult) · persistence(PageSupport) · presentation(PageResponse) · error · util(GeoUtils) · web(AuthSupport)
```

- **도메인으로 먼저 나누고, 그 안에서 계층으로 나눈다.** 계층을 최상위에 두지 않는다.
- **상위 도메인 없이 존재할 수 없는 하위 도메인은 상위 패키지 아래에 둔다** (`trip.record`).
  하위 패키지 안도 같은 계층으로 나눈다. 사진·태그는 기록에 딸려 있어도 최상위에 둔다 — 사진은
  바이너리 저장·파일 서빙·저장소 전환이라는 자기 관심사가 따로 있고, 태그는 여러 기록이 함께 쓴다.
- **`presentation` 에 컨트롤러와 요청·응답 DTO 를 함께 둔다.** 하위 패키지로 나누지 않고 파일
  이름(`*Controller`, `*Requests`, `*Responses`)으로 구분한다.
- **JPA 엔티티·리포지토리·Specifications 는 `persistence` 에 둔다.** 엔티티 클래스는 `*Entity`
  로 끝난다 (`TripEntity`). `domain` 은 저장 수단을 모르는 도메인 개념의 자리다 — `TripVisibility`·
  `Category`·`InviteOutcome`, 서비스가 주고받는 도메인 객체(`Trip`·`TripRef`·`TripRecord`·`GroupInvite`), 입력(`TripCreateCommand`),
  조회 조건(`TripListQuery`) 이 여기 있다.
- **의존 방향은 `presentation → service → persistence → domain` 이다.** 모든 계층이 `domain` 을
  알고, `domain` 은 아무것도 모른다.
  - `presentation` 은 `persistence` 를 모른다. 엔티티를 받거나 돌려주는 변환 함수를 두지 않는다.
  - 서비스·persistence 는 `presentation` 을 모른다. 요청 DTO·`PageResponse` 를 서비스 시그니처에 쓰지 않는다.
  - 외부 API 응답 모델은 그 API 를 부르는 `client` 에 둔다 (`NaverLocalSearchItem`).
- 두 도메인이 함께 쓰는 것만 `common` 으로 올린다. 한 곳에서만 쓰면 그 도메인에 둔다.
- import 는 **명시적으로 쓴다.** 와일드카드(`import ...*`)를 쓰지 않는다.

## 계층 책임

| 계층 | 하는 일 | 하지 않는 일 |
|---|---|---|
| Controller | 요청 바인딩·검증(`@Valid`), 인증 principal 해석, **요청 DTO → 도메인 입력, 도메인 객체 → 응답 DTO 변환** | 비즈니스 로직, 조회 |
| Service | 비즈니스 로직, 트랜잭션 경계, 권한 판정, **필요한 조회를 모두 끝내고 도메인 객체로 묶어 반환** | HTTP 관심사(상태 코드·헤더), 응답 모양 |
| Persistence | 데이터 접근. 엔티티·리포지토리·`Specifications` | 로직 분기 |

- **의존성은 생성자 주입**이다. 필드 주입(`@Autowired var`)을 쓰지 않는다.
- 컨트롤러 한 메서드의 모양은 다음과 같다 — 인증 확인, 요청 DTO → `Command`, 서비스 호출,
  도메인 객체 → 응답 DTO 순서다.

  ```kotlin
  @RestController
  @RequestMapping("/api/trips")
  class TripController(
      private val tripService: TripService,
  ) {
      @PostMapping
      fun create(
          @RequestBody @Valid request: TripCreateRequest,
          @AuthenticationPrincipal principal: LoginUser?,
      ): TripResponse {
          val userId = requireLogin(principal)  // permitAll 상태라 빠뜨리면 그대로 열린다 (아래 "인가")
          return TripResponse.from(tripService.create(userId, request.toCommand()), userId)
      }
  }
  ```

- **엔티티를 요청·응답에 직접 쓰지 않는다.** DTO로 주고받는다. DTO는 도메인별 `presentation` 패키지에
  용도별 파일로 모은다 (`RecordRequests.kt`, `RecordResponses.kt`).
- **서비스는 요청 DTO 를 받지 않는다.** 필드가 많은 생성·수정은 `domain` 의 `*Command`
  (`TripCreateCommand`) 로, 한두 개짜리 입력은 파라미터로 받는다 (`changeCover(tripId, ownerId, photoId)`).
  요청 DTO 의 `toCommand()` 가 옮겨 담는다. Bean Validation 은 요청 DTO 에만 건다.
- **서비스는 DTO 가 아니라 도메인 객체를 반환한다** (`Trip`, `Group`, `User` …).
  서비스가 응답 모양을 알면 화면이 바뀔 때마다 서비스가 끌려 들어온다.
- **응답 DTO 는 `companion object` 의 `from(도메인 객체[, requesterId])` 로 스스로 만든다.**
  컨트롤러가 `TripResponse.from(trip, userId)` 처럼 부른다. 도메인 객체에 `toXxxResponse()`
  확장 함수를 두지 않는다 — 변환이 DTO 밖에 흩어지면 응답 모양을 바꿀 때 찾아다녀야 한다.
- **응답을 만들면서 조회하지 않는다.** 변환 함수 안에서 리포지토리를 부르면 목록에서 그대로
  N+1 이 된다. 필요한 것은 서비스가 미리 모아 도메인 객체에 담는다 —
  `TripShareRepository.findByTripIdIn`, `PhotoRepository.findByRecordIdInOrderByCreatedAtAsc`
  이 그래서 있다.
- **요청자에 따라 달라지는 값은 도메인 객체가 필드로 들지 않는다.** `isOwner`·`isAuthor` 는
  변환 시점에 `isOwnedBy(userId)`·`isAuthoredBy(userId)` 로 정한다.
- **가릴 값은 읽지 않는 편이 확실하다.** 소유자에게만 나가는 `visibility`·`sharedGroups` 는
  응답에서 가리는 대신 소유자 조회일 때만 읽는다 (`TripService.sharesOf`).
- 서비스끼리 주고받는 값은 예외다. `GroupService.requireAccessibleGroups` 와
  `TagService.findOrCreateAll` 은 엔티티를 반환한다 — 컨트롤러로 나가는 경계가 아니라
  다른 서비스가 연관을 걸 때 쓰기 때문이다.
- **목록 서비스는 페이지 번호(`page: Int`)를 받아 `PageResult<도메인 객체>` 를 돌려준다.**
  Spring Data 의 `Page`·`Pageable` 은 서비스와 리포지토리 사이에서만 쓴다 — 컨트롤러는 이를
  만들지도 받지도 않는다. 서비스는 `listPageRequest(page, sort)` 로 요청을 만들고, 조회 결과를
  `toPageResult()` 로 바꾼다. 페이지 안에서 재정렬한 목록은 `copy(content = …)` 로 바꿔 담는다.
  응답은 컨트롤러가 `PageResponse.of(result).map { XxxResponse.from(it) }` 로 만든다.

## 인가 — 가장 조심할 자리

공개 범위 판정은 **빠뜨리는 순간 곧바로 정보 유출**이다. 명세 §2.2의 판정식을 그대로 따른다.

- **판정은 조회 쿼리 단계에서 한다.** 전부 읽어 온 뒤 애플리케이션에서 거르면 페이지 건수가
  어긋나고, 한 번 빠뜨리면 그대로 샌다.
- **기록 조회는 항상 소속 여행을 조인한다.** 판정 근거가 기록에 없으므로 조인 없는 조회 경로를
  만들면 그 경로가 곧 우회로가 된다. 목록·상세·태그 자동완성 모두 해당한다.
- **열람 권한이 없으면 `403` 이 아니라 `404`** 다. 없는 것과 응답 본문·메시지가 구분되지 않아야
  한다. 소속 여행을 못 봐서 가려지는 기록도 `RECORD_NOT_FOUND` 이지 `TRIP_NOT_FOUND` 가 아니다.
- **그룹은 예외로 `403` 이다.** 멤버가 아닌 그룹의 조회·탈퇴는 `403`, 없는 그룹만 `404` 다.
  그룹에서 가려지는 것은 멤버 명단이고 `403` 이 그것을 드러내지 않기 때문이다 (명세 §2.2.2).
  단 **여행 공유 요청에 담긴 접근 불가 그룹 id 는 그대로 `404`** 다 — 그 경로는 임의의 id 를
  넣어볼 수 있다.
- 인증은 현재 `SecurityConfig` 가 `permitAll()` 이라 컨트롤러가 `requireLogin(principal)` 로
  직접 막는다 (`common/web/AuthSupport.kt`). **이 상태를 전제로 새 엔드포인트를 만들 때도
  `requireLogin` 을 빠뜨리지 않는다.**

## 예외 처리

- 도메인 실패는 `throw ApiException(ErrorCode.XXX)` 로 던진다. 컨트롤러에서 상태 코드를 직접
  만들지 않는다.
- 새 실패 상황이 생기면 `ErrorCode` 에 추가하고 **명세 §6의 오류 표에도 함께 적는다.**
- `GlobalExceptionHandler` 가 컨트롤러 밖 실패(주소 없음·메서드 불일치·파싱 실패)까지 같은
  스키마로 변환한다. 새 핸들러를 따로 만들지 않는다.
- **`500`·`409` 응답 메시지는 고정 문구**다. 예외 메시지에 테이블·제약 이름이 드러날 수 있어
  응답에 싣지 않고 로그로만 남긴다.

## 영속성

- **soft delete 는 `deletedAt` 하나로 판단**한다. `isDeleted` 같은 플래그를 함께 두지 않는다.
  조회 제외는 엔티티의 `@SQLRestriction("deleted_at is null")` 이 처리한다.
- **수정 API가 `updatedAt` 을 응답에 담을 때는 `saveAndFlush`** 를 쓴다. `@PreUpdate` 가 flush
  시점에 돌아서, 그냥 `save` 하면 갱신 전 값이 나간다.
- 값 규칙 중 **평점 단위와 여행 기간 역전은 Bean Validation과 DB `CHECK` 양쪽**에서 막는다.
- 동적 조회 조건은 `XxxSpecifications` 에 모은다. 서비스에 쿼리 문자열을 흩지 않는다.

## 테스트

- 위치: `external-api/src/test/kotlin/com/yong/travel/` (배치는 `batch/src/test/...`), 파일명 `XxxTest.kt`. JUnit 5 + `kotlin-test-junit5`.
- **인가와 공개 범위는 반드시 테스트한다.** 기존 `RecordVisibilityTest` 가 기준이다 —
  비공개 접근 시 404, 그룹 탈퇴·삭제 시 즉시 차단, `scope` 로 권한이 넓어지지 않는지.
- 값 검증(평점 단위 등)은 경계값으로 확인한다.
