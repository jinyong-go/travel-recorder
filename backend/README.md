# 여행 지도 (travel-recorder) — Backend

다녀온 여행을 기록으로 남기고 원하는 상대에게만 공유하는 웹 애플리케이션의 백엔드 API 서버.

- 사용자가 **여행(Trip)** 을 만들고 그 하위에 방문 장소를 **여행 기록(TripRecord)** 으로 등록(사진·메모·평점)
- 공개 범위는 `PRIVATE` / `GROUP` / `PUBLIC` 중 **여행 단위로** 지정, 하위 기록은 소속 여행의 범위를 따름
- 기록 등록용 장소 검색은 NAVER API HUB 지역 검색을 백엔드가 프록시·집계해 제공

상세 요구사항은 [SPECIFICATION.md](./SPECIFICATION.md) 참고. 작업 규칙은 [AGENTS.md](./AGENTS.md) 참고.

## 기술 스택

| 구분 | 사용 기술 | 버전 |
|---|---|---|
| 언어 | Kotlin (JVM) | 2.3.21 |
| 프레임워크 | Spring Boot (Spring MVC) | 4.1.1 |
| 영속성 | Spring Data JPA / Hibernate | Boot 관리 |
| 데이터베이스 | PostgreSQL + PostGIS (모든 환경). 로컬은 Docker Compose | 이미지 `postgis/postgis:17-3.5` |
| 스키마 관리 | Flyway | Boot 관리 |
| 인증 | Spring Security (현재 임시 폼 로그인, 네이버 OAuth2 Client 복구 예정) | Boot 관리 |
| 직렬화 | Jackson (`jackson-module-kotlin`) | Boot 관리 |
| 입력 검증 | Bean Validation | Boot 관리 |
| 배치 | Spring Batch (메타데이터 테이블 없는 `ResourcelessJobRepository`) | Boot 관리 |
| 빌드 도구 | Gradle (Kotlin DSL, Wrapper 포함) | 9.7.1 |
| 테스트 | JUnit 5, `kotlin-test-junit5`, Spring Boot Test, Testcontainers (PostGIS) | Boot 관리 |

- 요구 JDK 버전: **17 이상** (루트 `build.gradle.kts`의 toolchain이 17로 고정)
- 세션 쿠키(`JSESSIONID`) 기반 인증. JWT 미사용
- 외부 API는 **NAVER API HUB 의 지역(Local) 검색**만 사용. 지도 렌더링은 프론트엔드 담당

## 모듈 구성

Gradle 멀티 모듈. 두 모듈은 서로 의존하지 않음.

```
backend/
├─ settings.gradle.kts     # 모듈 목록
├─ build.gradle.kts        # 공통 빌드 설정 (플러그인 버전, Kotlin 옵션, Java 17, JUnit)
├─ Dockerfile              # API 서버 이미지 (배치 소스는 .dockerignore로 제외)
├─ external-api/           # API 서버 — 아래 디렉터리 구조 참고
├─ batch/                  # 정기 정리 작업 (Spring Batch). 웹 서버 없이 잡 실행 후 종료
│  ├─ build.gradle.kts
│  └─ src/main/kotlin/com/yong/travel/batch/
│     ├─ BatchApplication.kt
│     └─ loginhistory/
│        ├─ LoginDailyStatsJobConfig.kt     # 로그인 이력의 하루 합계 집계
│        └─ LoginHistoryCleanupJobConfig.kt # 보관 기간이 지난 로그인 이력 삭제
└─ gradlew / gradlew.bat
```

- `batch`는 잡 하나를 실행하고 종료. 하루 한 번 외부 스케줄러(cron 등)가 아래 순서로 실행.
  잡이 둘이라 **잡 이름 지정 필수**이며, 실패하면 종료 코드가 0이 아님

  ```bash
  JAR=batch/build/libs/batch-0.0.1-SNAPSHOT.jar
  java -jar $JAR --spring.profiles.active=prod --spring.batch.job.name=loginDailyStatsJob      # 어제 로그인 합계
  java -jar $JAR --spring.profiles.active=prod --spring.batch.job.name=loginHistoryCleanupJob  # 보관 기간 지난 이력 삭제
  # 특정 날짜 재집계 (원본 보관 기간 안, 어제 이전 날짜만)
  java -jar $JAR --spring.profiles.active=prod --spring.batch.job.name=loginDailyStatsJob targetDate=2026-10-01
  ```
- 배치 메타데이터 테이블 없이 동작(`ResourcelessJobRepository`)하므로 DB에 추가할 테이블 없음
- `local`·`dev`·`prod`는 API 서버와 같은 DB 사용(`local`은 compose의 DB). 스키마는 API 서버의 Flyway가 만듦
- `test`는 빌드 시 `external-api`의 마이그레이션(`db/migration`)을 받아 Testcontainers DB에 Flyway로 적용

## 디렉터리 구조 (`external-api`)

도메인별 패키지 안에 `presentation` / `service` / `domain` / `persistence` 계층을 두는 구조.
JPA 엔티티·리포지토리·Specifications 는 `persistence`, 저장 수단과 무관한 도메인 개념
(`TripVisibility`, `Category`, `InviteOutcome`)은 `domain` 에 위치.

### 계층 구조와 의존 방향

```
presentation → service → persistence
      ↘           ↓          ↙
               domain
```

| 계층 | 담당 |
|---|---|
| `presentation` | 컨트롤러, 요청·응답 DTO. 요청 바인딩·검증, 요청 DTO → 도메인 입력, 도메인 객체 → 응답 DTO 변환 |
| `service` | 비즈니스 로직, 트랜잭션 경계, 권한 판정. 필요한 조회를 끝내고 도메인 객체로 반환 |
| `persistence` | JPA 엔티티·리포지토리·Specifications. 데이터 접근 |
| `domain` | 저장 수단과 무관한 도메인 개념·객체. 모든 계층이 참조 |

- **의존은 `presentation → service → persistence` 한 방향만 허용. 역참조 금지**
    - `service`·`persistence`는 `presentation`을 참조하지 않음 — 요청 DTO·`PageResponse`를 서비스 시그니처에 사용 금지
    - `persistence`는 `service`를 참조하지 않음
- **`presentation`은 `persistence`를 건너뛰어 참조하지 않음** — 엔티티를 받거나 반환하는 변환 함수 금지
- `domain`은 다른 계층을 참조하지 않음

세부 규칙은 [AGENTS.md](./AGENTS.md)의 "패키지"·"계층" 참고.

```
external-api/
├─ src/main/kotlin/com/yong/travel/
│  ├─ TravelRecordApplication.kt   # 엔트리 포인트
│  ├─ auth/                        # 인증·사용자
│  │  ├─ config/SecurityConfig.kt  # 시큐리티 필터체인, CSRF (oauth2Login 은 주석 처리)
│  │  ├─ config/LocalLoginConfig.kt  # 임시 고정 계정 (local·dev 전용)
│  │  ├─ presentation/LocalLoginController.kt  # 임시 로그인 POST /api/auth/login (local·dev 전용)
│  │  ├─ service/UserService.kt    # 네이버 프로필 평탄화 + 사용자 Upsert (OAuth 복구 시 사용)
│  │  ├─ security/CustomOAuth2User.kt
│  │  └─ persistence·presentation
│  ├─ trip/                        # 여행 — 기록의 상위 그룹이자 공유의 단위
│  │  ├─ persistence/TripEntity.kt, TripShareEntity.kt
│  │  ├─ persistence/TripSpecifications.kt  # 공개 범위 판정 (scope 조건)
│  │  ├─ domain/TripVisibility.kt
│  │  ├─ service·presentation
│  │  └─ record/                   # 여행 기록 CRUD 및 목록 조회 (반드시 여행 하나에 속한다)
│  │     ├─ persistence/TripRecordEntity.kt
│  │     ├─ persistence/TripRecordSpecifications.kt  # 소속 여행 조인 판정 + 카테고리·태그·키워드 조건
│  │     ├─ domain/Category.kt
│  │     └─ service·presentation
│  ├─ group/                       # 공유 그룹 (조회 전용 대상 목록), 초대와 초대 이력
│  │  ├─ persistence/GroupEntity.kt, GroupMemberEntity.kt, GroupInviteEntity.kt, InviteHistoryEntity.kt
│  │  ├─ domain/InviteOutcome.kt
│  │  └─ service·presentation
│  ├─ search/                      # NAVER API HUB 지역 검색 연동
│  │  ├─ client/NaverLocalSearchClient.kt
│  │  ├─ service/PlaceSearchService.kt      # 중복 제거·좌표 변환·페이징
│  │  └─ presentation/PlaceSearchController.kt  # GET /api/places/search (저장 단위가 아니라 외부 조회)
│  ├─ tag/                         # 태그 조회 (볼 수 있는 기록에 쓰인 태그로 제한)
│  ├─ photo/                       # 사진 업로드·삭제·서빙
│  │  ├─ config/                   # 업로드 제한 설정 (@ConfigurationProperties)
│  │  ├─ persistence/              # photos(메타데이터) · photo_data(바이너리) 엔티티
│  │  └─ storage/PhotoDatabaseService.kt  # 사진 바이너리 DB 저장·조회와 URL 생성
│  └─ common/
│     ├─ config/WebConfig.kt       # CORS, 쿼리 파라미터 enum 변환기
│     ├─ error/                    # ErrorCode, ApiException, GlobalExceptionHandler
│     ├─ web/AuthSupport.kt        # 인증 주체 → User 변환 헬퍼 (컨트롤러가 아니라 web 유지)
│     ├─ web/EnumParams.kt         # scope=mine 같은 소문자 enum 파라미터 변환
│     ├─ domain/PageResult.kt      # 서비스가 돌려주는 목록 한 페이지 (Spring Data 비의존)
│     ├─ persistence/PageSupport.kt  # 목록 페이지 크기(서버 고정), Page → PageResult 변환
│     └─ presentation/PageResponse.kt  # 공통 페이지 응답
├─ src/main/resources/
│  ├─ application.yml             # 공통 설정
│  ├─ application-local.yml       # 로컬 (compose 의 PostgreSQL, 초기 데이터 포함)
│  ├─ application-dev.yml         # 개발 서버 (PostgreSQL)
│  ├─ application-prod.yml        # 운영 (PostgreSQL, Flyway 끔)
│  ├─ db/migration/               # Flyway 마이그레이션 (V1__init.sql …)
│  ├─ db/seed/local/              # local 전용 초기 데이터 (R__local_seed.sql)
│  └─ META-INF/services/          # Hibernate 함수 등록 (PostGIS 거리 연산)
├─ src/test/kotlin/com/yong/travel/
└─ build.gradle.kts               # 이 모듈의 의존성
```

## 시작하기

### 1. 네이버 API 키 발급

역할이 다른 **두 종류의 키**가 필요하며, 각각 별도 애플리케이션에서 발급.

> **키 없이도 `local` 프로파일로 기동·사용 가능.** 로그인은 임시 계정, 장소 검색만 동작하지 않음.
> 네이버 로그인 키는 OAuth 복구 전까지 사용되지 않음 (SPECIFICATION.md 8.1).

| 용도 | 발급처 | 비고 |
|---|---|---|
| 네이버 로그인 | developers.naver.com | Callback URL에 `http://localhost:8080/login/oauth2/code/naver` 등록. 이메일·이름 권한을 "필수"로 설정해야 프로필 수신 가능 |
| 지역(Local) 검색 | ncloud.com (NAVER API HUB) | API Gateway 인증 키. 로그인용 키와 별개. 규격: [개요](https://api.ncloud-docs.com/docs/naver-api-hub-overview) · [지역 검색](https://api.ncloud-docs.com/docs/naver-api-hub-search-local) |

### 2. 환경 변수 설정

| 변수 | 필수 | 설명 |
|---|---|---|
| `NAVER_CLIENT_ID` | OAuth 복구 후 | 네이버 로그인 Client ID |
| `NAVER_CLIENT_SECRET` | OAuth 복구 후 | 네이버 로그인 Client Secret |
| `NAVER_API_CLIENT_ID` | 장소 검색 사용 시 | NAVER API HUB Client ID (`X-NCP-APIGW-API-KEY-ID`) |
| `NAVER_API_CLIENT_SECRET` | 장소 검색 사용 시 | NAVER API HUB Client Secret (`X-NCP-APIGW-API-KEY`) |
| `NAVER_API_BASE_URL` | 선택 | 지역 검색 주소 (기본 `https://naverapihub.apigw.ntruss.com/search/v1/local`) |
| `MANAGEMENT_PORT` | 선택 | 관리 엔드포인트(actuator) 포트 (기본 `8081`). 외부에 공개하지 않음 |

`dev` / `prod` 프로파일은 아래 값이 추가로 필요. (`local`은 compose의 DB 접속 정보가 기본값이라 불필요)

| 변수 | 필수 | 설명 |
|---|---|---|
| `DB_URL` | ✅ | PostgreSQL JDBC URL (예: `jdbc:postgresql://localhost:5432/travel`) |
| `DB_USERNAME` | ✅ | 데이터베이스 사용자 |
| `DB_PASSWORD` | ✅ | 데이터베이스 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | ✅ | 허용할 프론트엔드 오리진 (쉼표로 여러 개) |
| `DB_POOL_SIZE` | 선택 | HikariCP 최대 커넥션 수 (`prod`, 기본 10) |
| `LOG_FILE` | 선택 | 로그 파일 경로 (`prod`, 기본 `./logs/travel-recorder.log`) |

```bash
export NAVER_CLIENT_ID=...
export NAVER_CLIENT_SECRET=...
export NAVER_API_CLIENT_ID=...
export NAVER_API_CLIENT_SECRET=...
```

compose로 API 서버를 띄울 때(`docker compose up db api`)는 `export` 대신 루트 `.env`에 적음
(`.env.example` 참고). `bootRun`은 `.env`를 읽지 않으므로 위처럼 `export` 필요.

> 네이버 키 네 값 모두 기본값이 `changeit`이라 미설정 시에도 서버는 기동. 단 장소 검색은 동작하지 않음.

### 3. 개발 서버 실행

Docker (Compose 2.24 이상) 필요. compose 파일은 저장소 루트의 `compose.yml`이며 `docker compose`는 루트에서 실행.
DB와 API 서버를 함께 띄우는 방법과, DB만 띄우고 API 서버는 Gradle로 돌리는 방법이 있음.
배치는 compose 서비스에 포함하지 않음.

```bash
# 저장소 루트에서
docker compose up db api           # PostgreSQL(PostGIS) + API 서버 (프론트엔드까지는 docker compose up)

# 또는 DB 만 띄우고 API 서버는 IDE·Gradle 로
docker compose up db
cd backend && ./gradlew :external-api:bootRun
```

기본 주소는 http://localhost:8080. (Windows는 `./gradlew` 대신 `gradlew.bat`)
관리 엔드포인트는 별도 포트 8081 — 상태 확인 `/actuator/health`, 지표 `/actuator/prometheus`.
compose에서는 8081을 호스트에 열지 않으므로 bootRun으로 띄웠을 때만 `localhost:8081`로 접근 가능.

- 기동 시 Flyway가 스키마와 초기 데이터(사용자·그룹·여행·기록 목업)를 적용
- 데이터는 `db-data` 볼륨에 남음. 처음 상태로 되돌리려면 `docker compose down -v`
- DB 접속: `localhost:5432`, DB·사용자·비밀번호 모두 `travel`
- 공식 PostGIS 이미지는 amd64 전용이라 Apple Silicon에서는 에뮬레이션으로 실행됨

프로파일 미지정 시 `local` 적용. 다른 프로파일은 다음과 같이 지정.

```bash
./gradlew :external-api:bootRun --args='--spring.profiles.active=dev'
```

기동 후 확인 가능한 주소.

- API: `http://localhost:8080/api/...`
- 임시 로그인: `POST /api/auth/login` — `local`·`dev` 전용 고정 계정 `user1`·`user2`·`user3`, 비밀번호 `password`
  (계정이 셋이라 그룹 공유·초대를 직접 주고받기 가능)

## 사용 가능한 명령

| 명령 | 설명 |
|---|---|
| `./gradlew :external-api:bootRun` | API 서버 실행 (기본 포트 8080) |
| `./gradlew :batch:bootRun` | 배치 실행 (잡을 돌리고 종료) |
| `./gradlew build` | 컴파일 + 테스트 + 실행 가능한 JAR 생성 → `<모듈>/build/libs/` |
| `./gradlew test` | 모든 모듈 테스트 실행 (Testcontainers가 PostGIS 컨테이너를 띄우므로 Docker 필요) |
| `./gradlew clean` | 빌드 산출물 삭제 |

## 빌드 및 배포

```bash
./gradlew build                                                    # external-api/build/libs/external-api-0.0.1-SNAPSHOT.jar 생성
java -jar external-api/build/libs/external-api-0.0.1-SNAPSHOT.jar  # 빌드 결과 실행
```

```bash
java -jar external-api/build/libs/external-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

`prod` 프로파일 배포 시 위 [환경 변수](#2-환경-변수-설정) 표의 값 전부 주입 필요. `prod`는 Flyway가 꺼져 있어
**마이그레이션은 배포 절차에서 Flyway CLI로 직접 적용.** 앱은 `ddl-auto: validate`로 적용 여부만 확인.

> `prod`에는 임시 로그인 구성이 없고 OAuth도 꺼져 있어 **현재 로그인 수단 없음.**

```bash
docker run --rm -v "$PWD/external-api/src/main/resources/db/migration:/flyway/sql" flyway/flyway \
  -url="$DB_URL" -user="$DB_USERNAME" -password="$DB_PASSWORD" migrate
```

- 운영 DB에 `postgis`·`pg_trgm` 확장이 설치 가능해야 함. V1이 `CREATE EXTENSION`을 실행하므로
  마이그레이션 계정에 확장 생성 권한이 필요(또는 DBA가 미리 생성)

## 프로파일

공통 설정은 `application.yml`, 환경별로 달라지는 값(데이터소스, 마이그레이션, CORS, 로깅)만 프로파일 파일로 분리. 미지정 시 `local` 사용.

| 프로파일 | 데이터베이스 | `ddl-auto` | Flyway | 비고 |
|---|---|---|---|---|
| `local` (기본) | compose의 PostgreSQL | `validate` | 기동 시 적용 + 초기 데이터 | 앱 로그 `DEBUG`, 임시 로그인 |
| `dev` | PostgreSQL | `validate` | 기동 시 적용 | 앱 로그 `DEBUG`, 임시 로그인 |
| `prod` | PostgreSQL | `validate` | 꺼짐 (배포 절차에서 CLI) | 로그 `INFO`, HikariCP 풀 크기 조정 가능 |
| `test` | Testcontainers PostGIS | `validate` | 기동 시 적용 | 테스트 전용 |

## 데이터베이스 스키마

`external-api/src/main/resources/db/migration`의 Flyway 마이그레이션이 스키마의 기준. 엔티티(`com.yong.travel.*.persistence`)로부터 Hibernate가 생성하는 DDL에 맞춰 작성.

- 모든 프로파일이 `ddl-auto: validate`라 **엔티티와 스키마가 어긋나면 기동 실패.** 엔티티 변경 시 마이그레이션도 함께 추가.
- **이미 적용된 마이그레이션 파일은 수정하지 않음.** 변경은 다음 버전(`V2__…`) 파일로 추가.
- Flyway 도입 전에 `schema.sql`로 만든 개발·dev DB에는 이력 테이블이 없어 **재생성 필요.**
- **`trips.cover_photo_id`에는 외래키 없음.** 사진이 여행에서 사라지는 경로(사진 삭제·기록 삭제·기록의 소속 여행 변경)에서 애플리케이션이 커버 지정을 직접 해제. 근거는 [SPECIFICATION.md](./SPECIFICATION.md) 3 참고.
- PostgreSQL은 외래키에 인덱스를 자동 생성하지 않으므로, 조회·삭제에 쓰이는 FK 컬럼에 인덱스를 명시.
- `trips`와 `trip_records`는 **soft delete.** `deleted_at`이 `NULL`인 행만 유효하며, 엔티티의 `@SQLRestriction("deleted_at is null")`이 조회에서 자동 제외. 두 테이블의 인덱스는 `WHERE deleted_at IS NULL` 부분 인덱스. 여행 삭제 시 하위 기록도 같은 시각으로 함께 삭제하고, 하위 사진의 바이너리(`photo_data`)는 물리 삭제. 그룹·멤버·초대·공유 관계는 반대로 물리 삭제 — 탈퇴와 공유 해제는 즉시 조회 권한을 없애야 하기 때문. 정책과 근거는 [SPECIFICATION.md](./SPECIFICATION.md) 3.2 참고.
- **`trip_records`에는 `visibility`와 `author_id` 컬럼 없음.** 둘 다 소속 여행에서 파생되며, 같은 사실을 두 곳에 적으면 어긋나는 순간 어느 쪽이 맞는지 알 수 없기 때문. 그래서 기록 조회는 **항상 `trip_id`로 여행을 조인해** 공개 범위 판정.
- 확장: `postgis`(기록 거리순 정렬의 KNN 위치 인덱스), `pg_trgm`(이름·주소·태그 부분 일치 검색 인덱스).

## 설정 값

주요 값.

| 키 | 기본값 | 설명 |
|---|---|---|
| `app.cors.allowed-origins` | `http://localhost:5173` (local) | CORS 허용 오리진 (프론트엔드 dev 서버) |
| `app.upload.max-photo-size` | `5MB` | 서비스 레벨 사진 크기 검증 값 |
| `app.upload.allowed-content-types` | `image/jpeg,image/png,image/webp` | 업로드 허용 MIME 타입 |
| `spring.servlet.multipart.max-file-size` | `5MB` | 사진 1장의 최대 크기 |
| `spring.servlet.multipart.max-request-size` | `30MB` | 업로드 요청 전체의 최대 크기 |
| `logging.level.com.yong.travel` | `DEBUG` | 애플리케이션 로그 레벨 |

## API 개요

전체 스펙과 요청/응답 스키마는 [SPECIFICATION.md](./SPECIFICATION.md) 4장 참고.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/auth/login` | **임시** 로그인 (`local`·`dev` 전용). 네이버 OAuth 복구 시 `/oauth2/authorization/naver`로 대체 |
| `GET` | `/api/auth/session` | 클라이언트 부팅용 세션 상태 조회. `{ authenticated, csrfToken }`, 비로그인도 `200` |
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
| `POST` | `/api/records` | 기록 등록. `tripId` 필수, 요청자가 소유한 여행만 가능 |
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
| `GET` | `/api/invites/sent` | 내가 보낸 대기 중인 초대 목록 (그룹 횡단) |
| `GET` | `/api/invites/history` | 끝난 초대 이력. `role`(`received`\|`sent`) |
| `POST` | `/api/invites/{inviteId}/accept` | 초대 수락 (정원이 차 있으면 `409`) |
| `POST` | `/api/invites/{inviteId}/reject` | 초대 거절 |
| `POST` | `/api/records/{recordId}/photos` | 사진 업로드 (`multipart/form-data`, 여행 소유자) |
| `DELETE` | `/api/records/{recordId}/photos/{photoId}` | 사진 삭제 (여행 소유자) |
| `GET` | `/api/files/photos/{photoId}` | 사진 바이너리 서빙 (인증 불필요, 공개 범위 미적용) |

- `scope`와 `sort`는 **소문자로 전송.** 대문자도 허용 (`common/web/EnumParams.kt`).
- 목록의 **페이지 크기는 서버가 결정.** 요청의 `size`는 무시하며, 응답의 `size`가 적용된 값.

오류 응답은 `common/error`의 `ErrorResponse`(`code`/`message`/`status`) 형식으로 통일,
`ErrorCode`에 정의된 코드(`UNAUTHENTICATED`, `FORBIDDEN`, `TRIP_NOT_FOUND`, `RECORD_NOT_FOUND`,
`GROUP_NOT_FOUND`, `VALIDATION_ERROR`, `PLACE_SEARCH_UNAVAILABLE` 등)를 함께 반환.
**열람 권한이 없으면 `403`이 아니라 `404`** — 없는 것과 응답이 구분되지 않아야 하기 때문.
단 그룹은 예외로 멤버가 아니면 `403` (SPECIFICATION.md 2.2.2).

`GlobalExceptionHandler`는 컨트롤러가 던지는 `ApiException`뿐 아니라 **컨트롤러에 도달하기 전의
실패도 모두 같은 형식으로 변환** — 매핑되지 않은 주소(404), 메서드 불일치(405), `Content-Type` 불일치(415),
본문 파싱 실패·파라미터 타입 불일치(400), 업로드 용량 초과(400), DB 제약 위반(409), 마지막
`Exception` 캐치올(500). 500과 409는 내부 정보가 새지 않도록 고정 문구로 응답하고 스택은 로그에만 기록.
전체 목록은 [SPECIFICATION.md](./SPECIFICATION.md) 6장 참고.

## 프론트엔드 연동

- 프론트엔드는 같은 저장소의 `frontend/` (React + Vite), dev 서버 기본 포트 **5173**
- CORS 허용 오리진에 `http://localhost:5173`이 설정되어 있어 기본 포트로 실행하면 별도 프록시 불필요
- 세션 쿠키 인증이라 API 호출 시 `credentials: 'include'` 필요
- CSRF 보호 활성화. 토큰 쿠키가 HttpOnly라 SPA가 직접 읽지 못하므로, `GET /api/auth/session`
  응답의 `csrfToken`을 상태 변경 요청(`POST`/`PUT`/`PATCH`/`DELETE`)의 `X-XSRF-TOKEN` 헤더로 전송

## 현재 구현 상태

구현 완료
- 도메인 모델 및 API — 여행, 여행 기록, 공개 범위(여행 단위), 공유 그룹, 초대·초대 이력, 태그, 사진
- 공개 범위 판정을 조회 쿼리 단계에서 수행. 기록 목록·상세·태그 자동완성 모두 소속 여행을 조인해 판정
- 임시 인메모리 로그인 (`local`·`dev` 전용 고정 계정 셋)
- NAVER API HUB 지역 검색 프록시 (중복 제거, 좌표 변환, 페이징)
- 사진 DB 저장(`photo_data`, `BYTEA`) 및 `/api/files/photos/{photoId}` 서빙, 공통 예외 처리

미구현 / 예정 (상세는 SPECIFICATION.md 8장)
- **네이버 OAuth 로그인 복구** — `SecurityConfig`의 `oauth2Login` 블록이 주석 처리된 상태. 복구 시 `LocalLoginConfig`·`LocalLoginController` 제거
- **인가 정책 적용** — `SecurityConfig`가 개발 편의를 위해 `anyRequest().permitAll()`로 열린 상태. 인증은 컨트롤러가 `requireLogin`으로 직접 차단하며, SPECIFICATION.md 2.2의 정책대로 필터체인의 `authenticated()`로 복원 필요
- `trips.cover_photo_id` 외래키 복원 — Flyway 도입으로 가능해짐. 복원 시 `ON DELETE SET NULL`로 사진 삭제 경로의 커버 해제를 DB에 맡길 수 있음
- 사진 저장소의 AWS S3 전환 — 현재는 DB 저장, 저장 추상화는 전환 시점에 도입
- 사진 서빙에 공개 범위 미적용 — URL을 아는 사람은 비공개 여행의 사진도 열람 가능하며, 현재는 추측 불가능한 사진 id(UUID)에만 의존
- 장소 검색 후보 풀 확대 — 원본 API가 검색어당 최대 5건만 반환하므로, 검색어 변형으로 추가 호출해 집계하는 로직 필요
