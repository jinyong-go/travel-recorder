# backend/CLAUDE.md

백엔드 모듈에서 작업할 때의 규칙이다. 승인 절차·명세 작업·코드 원칙·주석 규칙은
[루트 CLAUDE.md](../CLAUDE.md)에 있고 **여기서 반복하지 않는다.**

요구사항은 [`SPECIFICATION.md`](./SPECIFICATION.md)를 따른다. 명세에 없는 결정이 필요하면
코드로 정하지 말고 묻는다.

## 명령

```bash
./gradlew bootRun     # 실행 (기본 local 프로파일, H2 인메모리)
./gradlew test        # 테스트
./gradlew build       # 빌드 (테스트 포함)
```

- 프로파일: `local`(H2, `MODE=PostgreSQL`) / `dev` / `prod`(PostgreSQL)
- **세 프로파일 모두 `ddl-auto: validate`** 다. 엔티티와 `schema.sql` 이 어긋나면 기동조차 되지
  않으므로, **엔티티를 고치면 `schema.sql` 도 같이 고친다.**

## 스택

Kotlin 2.3 / Spring Boot 4.1 / Spring Data JPA / Spring Security OAuth2 Client / Bean Validation

## 패키지 구조

```
com.yong.travel
  ├─ auth     인증·사용자 (config, controller, domain, dto, repository, security, service)
  ├─ record   방문 기록
  ├─ group    공유 그룹·초대
  ├─ photo    사진 (storage 하위에 저장소 구현체)
  ├─ search   장소 검색 (client 하위에 외부 API 호출)
  ├─ tag      태그
  └─ common   config · dto(PageResponse) · error · util(GeoUtils) · web(AuthSupport)
```

- **도메인으로 먼저 나누고, 그 안에서 계층으로 나눈다.** 계층을 최상위에 두지 않는다.
- 두 도메인이 함께 쓰는 것만 `common` 으로 올린다. 한 곳에서만 쓰면 그 도메인에 둔다.
- import 는 **명시적으로 쓴다.** 와일드카드(`import ...*`)를 쓰지 않는다.

## 계층 책임

| 계층 | 하는 일 | 하지 않는 일 |
|---|---|---|
| Controller | 요청 바인딩·검증(`@Valid`), 인증 principal 해석, DTO 반환 | 비즈니스 로직, 엔티티 직접 반환 |
| Service | 비즈니스 로직, 트랜잭션 경계, 권한 판정 | HTTP 관심사(상태 코드·헤더) |
| Repository | 데이터 접근. 동적 조건은 `Specifications` | 로직 분기 |

- **의존성은 생성자 주입**이다. 필드 주입(`@Autowired var`)을 쓰지 않는다.

  ```kotlin
  @RestController
  @RequestMapping("/api/records")
  class TripRecordController(
      private val recordService: TripRecordService,
  )
  ```

- **엔티티를 요청·응답에 직접 쓰지 않는다.** DTO로 주고받는다. DTO는 도메인별 `dto` 패키지에
  용도별 파일로 모은다 (`RecordRequests.kt`, `RecordResponses.kt`, `RecordListQuery.kt`).
- 목록 응답은 공통 `PageResponse` 를 쓴다.

## 인가 — 가장 조심할 자리

공개 범위 판정은 **빠뜨리는 순간 곧바로 정보 유출**이다. 명세 §2.2의 판정식을 그대로 따른다.

- **판정은 조회 쿼리 단계에서 한다.** 전부 읽어 온 뒤 애플리케이션에서 거르면 페이지 건수가
  어긋나고, 한 번 빠뜨리면 그대로 샌다.
- **기록 조회는 항상 소속 여행을 조인한다.** 판정 근거가 기록에 없으므로 조인 없는 조회 경로를
  만들면 그 경로가 곧 우회로가 된다. 목록·상세·태그 자동완성 모두 해당한다.
- **열람 권한이 없으면 `403` 이 아니라 `404`** 다. 없는 것과 응답 본문·메시지가 구분되지 않아야
  한다. 소속 여행을 못 봐서 가려지는 기록도 `RECORD_NOT_FOUND` 이지 `TRIP_NOT_FOUND` 가 아니다.
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

- 위치: `src/test/kotlin/com/yong/travel/`, 파일명 `XxxTest.kt`. JUnit 5 + `kotlin-test-junit5`.
- **인가와 공개 범위는 반드시 테스트한다.** 기존 `RecordVisibilityTest` 가 기준이다 —
  비공개 접근 시 404, 그룹 탈퇴·삭제 시 즉시 차단, `scope` 로 권한이 넓어지지 않는지.
- 값 검증(평점 단위 등)은 경계값으로 확인한다.
