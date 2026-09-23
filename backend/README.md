# 여행 지도 (travel-recorder) — Backend

다녀온 여행을 기록으로 남기고 원하는 상대에게만 공유하는 웹 애플리케이션의 백엔드 API 서버입니다.
네이버 계정으로 로그인한 사용자가 **여행(Trip)** 을 만들고 그 아래에 방문한 장소를
**여행 기록(TripRecord)** 으로 남깁니다(사진·메모·평점). 공개 범위는 `PRIVATE` / `GROUP` / `PUBLIC`
중에서 **여행 단위로** 정하며, 하위 기록은 소속 여행의 범위를 그대로 따릅니다. 기록 등록 시
사용하는 장소 검색은 네이버 지역 검색 오픈API를 백엔드가 프록시·집계해 제공합니다.

상세 요구사항은 [SPECIFICATION.md](./SPECIFICATION.md)를 참고하세요.

## 기술 스택

| 구분 | 사용 기술 | 버전 |
|---|---|---|
| 언어 | Kotlin (JVM) | 2.3.21 |
| 프레임워크 | Spring Boot (Spring MVC) | 4.1.1 |
| 영속성 | Spring Data JPA / Hibernate | Boot 관리 |
| 데이터베이스 | H2 (local, in-memory) / PostgreSQL (dev·prod) | Boot 관리 |
| 인증 | Spring Security OAuth2 Client (네이버 로그인) | Boot 관리 |
| 직렬화 | Jackson (`jackson-module-kotlin`) | Boot 관리 |
| 입력 검증 | Bean Validation | Boot 관리 |
| 리액티브 | Project Reactor (`reactor-core`, Kotlin 확장) | 3.8.7 |
| 빌드 도구 | Gradle (Kotlin DSL, Wrapper 포함) | 9.7.1 |
| 테스트 | JUnit 5, `kotlin-test-junit5`, Spring Boot Test | — |

- 요구 JDK 버전: **17 이상** (`build.gradle.kts`의 toolchain이 17로 고정되어 있습니다)
- 세션 쿠키(`JSESSIONID`) 기반 인증을 사용하며, JWT는 사용하지 않습니다.
- 외부 API는 **네이버 검색 오픈API의 지역(Local) 검색**을 사용합니다. 지도 렌더링은
  프론트엔드가 직접 담당하므로 백엔드는 관여하지 않습니다.

## 디렉터리 구조

도메인별 패키지 안에 `domain` / `persistence` / `service` / `controller` / `dto` 계층을 두는 구조입니다.
JPA 엔티티·리포지토리·Specifications 는 `persistence` 에 있고, `domain` 에는 저장 수단과 무관한
도메인 개념(`Visibility`, `Category`, `InviteOutcome`)이 있습니다.

```
backend/
├─ src/main/kotlin/com/yong/travel/
│  ├─ BackendApplication.kt        # 엔트리 포인트
│  ├─ auth/                        # 네이버 OAuth2 로그인
│  │  ├─ config/SecurityConfig.kt  # 시큐리티 필터체인, CSRF, oauth2Login
│  │  ├─ service/UserService.kt    # 네이버 프로필(response 래핑) 평탄화 + 사용자 Upsert
│  │  ├─ security/CustomOAuth2User.kt
│  │  └─ persistence·controller·dto
│  ├─ trip/                        # 여행 — 기록의 상위 그룹이자 공유의 단위
│  │  ├─ persistence/Trip.kt, TripShare.kt
│  │  ├─ persistence/TripSpecifications.kt  # 공개 범위 판정 (scope 조건)
│  │  ├─ domain/Visibility.kt
│  │  └─ service·controller·dto
│  ├─ record/                      # 여행 기록 CRUD 및 목록 조회 (반드시 여행 하나에 속한다)
│  │  ├─ persistence/TripRecord.kt
│  │  ├─ persistence/TripRecordSpecifications.kt  # 소속 여행 조인 판정 + 카테고리·태그·키워드 조건
│  │  ├─ domain/Category.kt
│  │  └─ service·controller·dto
│  ├─ group/                       # 공유 그룹 (조회 전용 대상 목록) 과 초대 목록
│  │  ├─ persistence/Group.kt, GroupMember.kt, GroupInvite.kt, InviteHistory.kt
│  │  ├─ domain/InviteOutcome.kt
│  │  └─ service·controller·dto
│  ├─ search/                      # 네이버 지역 검색 오픈API 연동
│  │  ├─ client/NaverLocalSearchClient.kt
│  │  ├─ service/PlaceSearchService.kt      # 중복 제거·거리순 정렬·페이징
│  │  └─ controller/PlaceSearchController.kt  # GET /api/places/search (저장 단위가 아니라 외부 조회)
│  ├─ tag/                         # 태그 조회 (볼 수 있는 기록에 쓰인 태그로 제한)
│  ├─ photo/                       # 사진 업로드·삭제
│  │  ├─ config/                   # 업로드 제한·저장소 설정 (@ConfigurationProperties)
│  │  └─ storage/                  # PhotoStorageService 추상화 + 파일시스템 구현체
│  └─ common/
│     ├─ config/WebConfig.kt       # CORS, 사진 정적 리소스 핸들러, 쿼리 파라미터 enum 변환기
│     ├─ error/                    # ErrorCode, ApiException, GlobalExceptionHandler
│     ├─ web/AuthSupport.kt        # 인증 주체 → User 변환 헬퍼 (컨트롤러가 아니라 web 유지)
│     ├─ web/EnumParams.kt         # scope=mine 같은 소문자 enum 파라미터 변환
│     ├─ web/PageSupport.kt        # 목록 페이지 크기(서버 고정) 처리
│     ├─ util/GeoUtils.kt          # 하버사인 거리 계산
│     └─ dto/PageResponse.kt       # 공통 페이지 응답
├─ src/main/resources/
│  ├─ application.yml             # 공통 설정
│  ├─ application-local.yml       # 로컬 (H2 in-memory, PostgreSQL 호환 모드)
│  ├─ application-dev.yml         # 개발 서버 (PostgreSQL)
│  ├─ application-prod.yml        # 운영 (PostgreSQL)
│  └─ schema.sql                  # 엔티티 기준 DDL
├─ src/test/kotlin/com/yong/travel/
├─ build.gradle.kts
└─ gradlew / gradlew.bat
```

## 시작하기

### 1. 네이버 API 키 발급

역할이 다른 **두 종류의 키**가 필요하며, 서로 다른 애플리케이션에서 발급받습니다.

| 용도 | 발급처 | 비고 |
|---|---|---|
| 네이버 로그인 | developers.naver.com | Callback URL에 `http://localhost:8080/login/oauth2/code/naver` 등록. 이메일·이름 권한을 "필수"로 설정해야 프로필을 받을 수 있습니다 |
| 지역(Local) 검색 | developers.naver.com | 검색 오픈API용 애플리케이션. 로그인용 키와 별개입니다 |

### 2. 환경 변수 설정

| 변수 | 필수 | 설명 |
|---|---|---|
| `NAVER_CLIENT_ID` | 로그인 사용 시 | 네이버 로그인 Client ID |
| `NAVER_CLIENT_SECRET` | 로그인 사용 시 | 네이버 로그인 Client Secret |
| `NAVER_SEARCH_CLIENT_ID` | 장소 검색 사용 시 | 지역 검색 오픈API Client ID |
| `NAVER_SEARCH_CLIENT_SECRET` | 장소 검색 사용 시 | 지역 검색 오픈API Client Secret |

`dev` / `prod` 프로파일은 아래 값이 추가로 필요합니다. (`local`은 인메모리 H2라 불필요합니다)

| 변수 | 필수 | 설명 |
|---|---|---|
| `DB_URL` | ✅ | PostgreSQL JDBC URL (예: `jdbc:postgresql://localhost:5432/travel`) |
| `DB_USERNAME` | ✅ | 데이터베이스 사용자 |
| `DB_PASSWORD` | ✅ | 데이터베이스 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | ✅ | 허용할 프론트엔드 오리진 (쉼표로 여러 개) |
| `PHOTO_STORAGE_DIR` | `prod` 필수 | 사진 저장 디렉터리. 재배포 시에도 유지되는 경로여야 합니다 |
| `DB_POOL_SIZE` | 선택 | HikariCP 최대 커넥션 수 (`prod`, 기본 10) |

```bash
export NAVER_CLIENT_ID=...
export NAVER_CLIENT_SECRET=...
export NAVER_SEARCH_CLIENT_ID=...
export NAVER_SEARCH_CLIENT_SECRET=...
```

> 네 값 모두 기본값이 `changeit`이라 설정하지 않아도 서버는 기동되지만, 로그인과 장소 검색은 동작하지 않습니다.

### 3. 개발 서버 실행

```bash
cd backend
./gradlew bootRun
```

기본 주소는 http://localhost:8080 입니다. (Windows는 `./gradlew` 대신 `gradlew.bat`)

프로파일을 지정하지 않으면 `local`이 적용됩니다. 다른 프로파일로 띄우려면 다음과 같이 지정합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

기동 후 확인할 수 있는 주소는 다음과 같습니다.

- API: `http://localhost:8080/api/...`
- 네이버 로그인 시작: `http://localhost:8080/oauth2/authorization/naver`
- H2 콘솔: `http://localhost:8080/h2-console`
  (JDBC URL `jdbc:h2:mem:travel-recorder`, 사용자 `sa`, 비밀번호 없음)

> `local` 프로파일의 H2는 인메모리이므로 **서버를 재시작하면 데이터가 모두 사라집니다.**

## 사용 가능한 명령

| 명령 | 설명 |
|---|---|
| `./gradlew bootRun` | 개발 서버 실행 (기본 포트 8080) |
| `./gradlew build` | 컴파일 + 테스트 + 실행 가능한 JAR 생성 → `build/libs/` |
| `./gradlew test` | 테스트만 실행 |
| `./gradlew clean` | 빌드 산출물 삭제 |

## 빌드 및 배포

```bash
./gradlew build                                  # build/libs/backend-0.0.1-SNAPSHOT.jar 생성
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar  # 빌드 결과 실행
```

```bash
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

`prod` 프로파일로 배포할 때는 위 [환경 변수](#2-환경-변수-설정) 표의 값을 모두 주입해야 하며, `spring.sql.init.mode`가 `never`이므로 **`schema.sql`은 배포 절차에서 직접 적용**해야 합니다.

```bash
psql "$DB_URL" -f src/main/resources/schema.sql
```

## 프로파일

공통 설정은 `application.yml`에 두고, 환경별로 달라지는 값(데이터소스, 스키마 초기화, CORS, 로깅)만 프로파일 파일로 분리했습니다. 프로파일을 지정하지 않으면 `local`이 사용됩니다.

| 프로파일 | 데이터베이스 | `ddl-auto` | `schema.sql` 실행 | 비고 |
|---|---|---|---|---|
| `local` (기본) | H2 in-memory (`MODE=PostgreSQL`) | `validate` | 기동 시마다 | H2 콘솔 활성화, 앱 로그 `DEBUG` |
| `dev` | PostgreSQL | `validate` | 기동 시마다 | 앱 로그 `DEBUG` |
| `prod` | PostgreSQL | `validate` | 실행하지 않음 | 로그 `INFO`, HikariCP 풀 크기 조정 가능 |

## 데이터베이스 스키마

`src/main/resources/schema.sql`이 스키마의 기준이며, 엔티티(`com.yong.travel.*.persistence`)로부터 Hibernate가 생성하는 DDL에 맞춰 작성되어 있습니다.

- 모든 프로파일이 `ddl-auto: validate`이므로, **엔티티와 `schema.sql`이 어긋나면 기동 시점에 실패합니다.** 엔티티를 바꿀 때는 `schema.sql`도 함께 고쳐야 합니다.
- 스크립트는 `CREATE TABLE IF NOT EXISTS` 기반이고 외래키를 `CREATE TABLE` 안에 인라인으로 선언해 **재실행해도 안전**합니다. 이 때문에 테이블은 참조 순서(`users` → `tags` → `share_group` → `trips` → `trip_records` → 나머지)로 정의되어 있습니다.
- **`trips.cover_photo_id`에는 외래키가 없습니다.** `trips` → `photos` → `trip_records` → `trips` 순환이라 인라인으로 선언할 수 없고, `ALTER TABLE ADD CONSTRAINT`는 PostgreSQL에 `IF NOT EXISTS`가 없어 재실행되는 이 스크립트에서 실패합니다. 대신 사진이 여행에서 사라지는 경로(사진 삭제·기록 삭제·기록의 소속 여행 변경)에서 애플리케이션이 커버 지정을 직접 해제합니다. 근거는 [SPECIFICATION.md](./SPECIFICATION.md) 3을 참고하세요.
- `local`은 H2를 `MODE=PostgreSQL`로 띄워 dev/prod와 같은 스크립트를 그대로 사용합니다.
- `prod`는 `spring.sql.init.mode: never`라 애플리케이션이 DDL을 실행하지 않습니다. 스키마 적용은 배포 절차에서 별도로 수행해야 합니다.
- PostgreSQL은 외래키에 인덱스를 자동 생성하지 않으므로, 조회·삭제에 쓰이는 FK 컬럼에 인덱스를 명시해 두었습니다.
- `trips`와 `trip_records`는 **soft delete**를 사용합니다. `deleted_at`이 `NULL`인 행만 살아 있는 행이며, 엔티티의 `@SQLRestriction("deleted_at is null")`이 조회에서 자동으로 제외합니다. 여행을 지우면 하위 기록도 같은 시각으로 함께 지웁니다. 그룹·멤버·초대·공유 관계는 반대로 물리 삭제합니다 — 탈퇴와 공유 해제는 즉시 조회 권한을 없애야 하기 때문입니다. 정책과 근거는 [SPECIFICATION.md](./SPECIFICATION.md) 3.2를 참고하세요.
- **`trip_records`에는 `visibility`와 `author_id` 컬럼이 없습니다.** 둘 다 소속 여행에서 파생되며, 같은 사실을 두 곳에 적으면 어긋나는 순간 어느 쪽이 맞는지 알 수 없기 때문입니다. 그래서 기록 조회는 **항상 `trip_id`로 여행을 조인해** 공개 범위를 판정합니다.
- `CREATE TABLE IF NOT EXISTS`는 **이미 존재하는 테이블에 컬럼을 추가하지 못합니다.** 여행 계층 전환은 `schema.sql`을 새로 써서 반영했으므로 **기존 개발·dev 데이터베이스는 재생성해야 합니다.** 실제 데이터가 쌓이기 시작하면 변경 이력을 남길 마이그레이션 도구가 필요합니다.

## 설정 값

주요 값은 다음과 같습니다.

| 키 | 기본값 | 설명 |
|---|---|---|
| `app.storage.type` | `filesystem` | 사진 저장소 종류 (`s3`는 후속 과제) |
| `app.storage.local.root-dir` | `./uploads` (local) | 사진 저장 디렉터리. `/api/files/photos/**` 경로로 정적 서빙됩니다 |
| `app.cors.allowed-origins` | `http://localhost:5173` (local) | CORS 허용 오리진 (프론트엔드 dev 서버) |
| `app.upload.max-photo-size` | `5MB` | 서비스 레벨 사진 크기 검증 값 |
| `app.upload.allowed-content-types` | `image/jpeg,image/png,image/webp` | 업로드 허용 MIME 타입 |
| `spring.servlet.multipart.max-file-size` | `5MB` | 사진 1장의 최대 크기 |
| `spring.servlet.multipart.max-request-size` | `30MB` | 업로드 요청 전체의 최대 크기 |
| `logging.level.com.yong.travel` | `DEBUG` | 애플리케이션 로그 레벨 |

## API 개요

전체 스펙과 요청/응답 스키마는 [SPECIFICATION.md](./SPECIFICATION.md) 4장을 참고하세요.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/oauth2/authorization/naver` | 네이버 로그인 시작 (Spring Security 기본 처리) |
| `GET` | `/api/auth/me` | 현재 로그인 사용자 조회 |
| `POST` | `/api/auth/logout` | 로그아웃 (세션 무효화) |
| `GET` | `/api/trips` | 여행 목록. `scope`(`mine`\|`shared`\|`public`) **필수**, `keyword`, `sort`(`recent`\|`startDate`), `page` |
| `GET` | `/api/trips/{tripId}` | 여행 상세. 볼 권한이 없으면 `404` |
| `POST` | `/api/trips` | 여행 생성 (공개 범위 생략 시 `PRIVATE`) |
| `PUT` | `/api/trips/{tripId}` | 여행 기본 정보 수정 — 공개 범위 제외 (소유자) |
| `PATCH` | `/api/trips/{tripId}/visibility` | 공개 범위·공유 그룹만 변경 (소유자) |
| `PATCH` | `/api/trips/{tripId}/cover` | 커버 사진 지정·해제 (소유자) |
| `DELETE` | `/api/trips/{tripId}` | 여행 삭제. 하위 기록도 함께 soft delete (소유자) |
| `GET` | `/api/records` | 기록 목록. `scope` **필수**, `tripId`, `category`, `tag`, `keyword`, `sort`, `lat`, `lng`, `page` |
| `GET` | `/api/records/{recordId}` | 기록 상세. 볼 권한이 없으면 `404` |
| `POST` | `/api/records` | 기록 등록. `tripId` 필수이며 요청자가 소유한 여행이어야 합니다 |
| `PUT` | `/api/records/{recordId}` | 기록 수정 (여행 소유자) |
| `PATCH` | `/api/records/{recordId}/trip` | 소속 여행 변경 (여행 소유자) |
| `DELETE` | `/api/records/{recordId}` | 기록 삭제 (여행 소유자) |
| `GET` | `/api/places/search` | 등록 폼용 장소 검색 (네이버 지역 검색 프록시·집계) |
| `GET` | `/api/tags` | 태그 검색 (`keyword`). 볼 수 있는 기록에 쓰인 태그로 제한 |
| `GET` | `/api/groups` | 내가 소유하거나 참여한 그룹 목록 |
| `POST` | `/api/groups` | 그룹 생성 (소유자도 멤버로 입력) |
| `GET` | `/api/groups/{groupId}` | 그룹 상세 (멤버 목록). 멤버가 아니면 `404` |
| `PUT` \| `DELETE` | `/api/groups/{groupId}` | 그룹 이름 변경 / 삭제 (소유자) |
| `DELETE` | `/api/groups/{groupId}/members/me` | 그룹 탈퇴 (소유자는 `403`) |
| `DELETE` | `/api/groups/{groupId}/members/{userId}` | 멤버 제외 (소유자) |
| `GET` \| `POST` | `/api/groups/{groupId}/invites` | 대기 중인 초대 목록 / 이메일로 초대 발송 (소유자) |
| `DELETE` | `/api/groups/{groupId}/invites/{inviteId}` | 초대 철회 (소유자) |
| `GET` | `/api/invites` | 내가 받은 초대 목록 |
| `POST` | `/api/invites/{inviteId}/accept` | 초대 수락 (정원이 차 있으면 `409`) |
| `POST` | `/api/invites/{inviteId}/reject` | 초대 거절 |
| `POST` | `/api/records/{recordId}/photos` | 사진 업로드 (`multipart/form-data`, 여행 소유자) |
| `DELETE` | `/api/records/{recordId}/photos/{photoId}` | 사진 삭제 (여행 소유자) |

- `scope`와 `sort`는 **소문자로 보냅니다.** 대문자도 받습니다 (`common/web/EnumParams.kt`).
- 목록의 **페이지 크기는 서버가 정합니다.** 요청에 `size`를 담아도 무시하며, 응답의 `size`가 적용된 값입니다.

오류 응답은 `common/error`의 `ErrorResponse`(`code`/`message`/`status`) 형식으로 통일되어 있으며,
`ErrorCode`에 정의된 코드(`UNAUTHENTICATED`, `FORBIDDEN`, `TRIP_NOT_FOUND`, `RECORD_NOT_FOUND`,
`GROUP_NOT_FOUND`, `VALIDATION_ERROR`, `PLACE_SEARCH_UNAVAILABLE` 등)를 함께 내려줍니다.
**열람 권한이 없으면 `403`이 아니라 `404`입니다** — 없는 것과 응답이 구분되지 않아야 하기 때문입니다.

`GlobalExceptionHandler`는 컨트롤러가 던지는 `ApiException`뿐 아니라 **컨트롤러에 도달하기 전에 발생하는
실패도 모두 같은 형식으로 변환**합니다 — 매핑되지 않은 주소(404), 메서드 불일치(405), `Content-Type` 불일치(415),
본문 파싱 실패·파라미터 타입 불일치(400), 업로드 용량 초과(400), DB 제약 위반(409), 그리고 마지막
`Exception` 캐치올(500)입니다. 500과 409는 내부 정보가 새지 않도록 고정 문구로 응답하고 스택은 로그에만 남깁니다.
전체 목록은 [SPECIFICATION.md](./SPECIFICATION.md) 6장을 참고하세요.

## 프론트엔드 연동

- 프론트엔드는 같은 저장소의 `frontend/` (React + Vite)이며 dev 서버 기본 포트는 **5173**입니다.
- CORS 허용 오리진에 `http://localhost:5173`이 이미 설정되어 있어, 기본 포트로 실행하면 별도 프록시 없이 호출할 수 있습니다.
- 세션 쿠키 인증이므로 API 호출 시 `credentials: 'include'`가 필요합니다.
- CSRF 보호가 켜져 있습니다. 상태 변경 요청(`POST`/`PUT`/`DELETE`)은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 되돌려 보내야 합니다.

## 현재 구현 상태

구현 완료
- 도메인 모델 및 API — 여행, 여행 기록, 공개 범위(여행 단위), 공유 그룹, 초대, 태그, 사진
- 공개 범위 판정을 조회 쿼리에 싣습니다. 기록 목록·상세·태그 자동완성 모두 소속 여행을 조인해 판정합니다
- 네이버 OAuth2 로그인 연동 및 사용자 Upsert
- 네이버 지역 검색 오픈API 프록시 (중복 제거, 거리순 정렬, 페이징)
- 파일시스템 사진 저장소 및 정적 서빙, 공통 예외 처리

미구현 / 예정 (상세는 SPECIFICATION.md 8장)
- **인가 정책 적용** — `SecurityConfig`가 개발 편의를 위해 `anyRequest().permitAll()`로 열려 있습니다. 인증은 컨트롤러가 `requireLogin`으로 직접 막고 있으며, SPECIFICATION.md 2.2의 정책대로 필터체인의 `authenticated()`로 되돌려야 합니다.
- 스키마 마이그레이션 도구 도입 (Flyway/Liquibase) — 현재는 `schema.sql` 단일 파일이라 기존 테이블의 변경 이력을 관리할 수 없습니다. 도입하면 `trips.cover_photo_id`에 외래키를 되돌릴 수 있습니다.
- 사진 저장소의 AWS S3 전환 (`app.storage.type: s3`)
- 사진 서빙에 공개 범위가 적용되지 않습니다 — 경로를 아는 사람은 비공개 여행의 사진도 볼 수 있으며, 현재는 추측 불가능한 UUID 경로에만 의존합니다.
- 장소 검색 후보 풀 확대 — 원본 API가 검색어당 최대 5건만 반환하므로, 검색어 변형으로 추가 호출해 집계하는 로직이 필요합니다.
