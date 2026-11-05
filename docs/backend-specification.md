# 여행 지도 (travel-recorder) 백엔드 요구사항 정의서

## 1. 개요

### 1.1 이 문서의 범위
본 문서는 `backend/` 가 **무엇을 어떻게 구현하는지**를 정의한다 — 엔티티·테이블, 인증/인가 구현,
사진 저장소 설계. REST API 시그니처와 오류 코드는 [`api-specification.md`](./api-specification.md)(이하 API 명세)
가 소유한다.

**서비스가 무엇인지는 여기서 정의하지 않는다.** 도메인 개념과 규칙, 공개 범위 정책, 외부 API의
구조적 제약, 모듈 간 계약은 [`specification.md`](./specification.md) 가 소유하며
**충돌 시 공통 명세가 우선한다.** 본 문서는 그 규칙을 코드로 옮기는 방법만 적고, 규칙 자체를
다시 서술하지 않는다. 화면 요구사항은
[`frontend-specification.md`](./frontend-specification.md) 를 따른다.

용어는 [공통 명세 §3](./specification.md) 을 그대로 쓴다. 본 문서에서만 이름이 다른 것은
없다. 본문의 `Trip`·`TripRecord`·`Group` … 은 §3의 괄호 표기를 따른 개념 이름이며, JPA 엔티티
클래스는 여기에 `Entity` 접미사를 붙인다 (`TripEntity`, `TripRecordEntity`, `GroupEntity` …).
접미사 없는 이름은 서비스가 주고받는 도메인 객체의 몫이다.

### 1.2 실행 환경
- Kotlin 2.3 / Spring Boot 4.1 / Spring Data JPA / Spring Security OAuth2 Client
- 데이터베이스는 모든 환경에서 PostgreSQL + PostGIS 다. `local` 은 Docker Compose 로, 테스트는
  Testcontainers 로 띄운다. 기록 거리순 정렬이 PostGIS 를, 부분 일치 검색 인덱스가 `pg_trgm` 을 쓴다.
- 스키마는 Flyway 마이그레이션으로 관리한다. `local`·`dev`·`test` 는 기동 시 적용하고, `prod` 는
  애플리케이션이 DDL 을 실행하지 않도록 끄고 배포 절차에서 적용한다. 모든 프로파일이 `ddl-auto: validate`
  라 엔티티와 스키마가 어긋나면 기동 시점에 실패한다.
- OAuth2 클라이언트는 **네이버만 등록되어 있다.** 제공자를 더하는 방법은 §2.1 에 있다.

> 여행 계층과 인가 정책(§2.2)은 구현되어 있다. 남은 차이는 §6.1에 있다.

### 1.3 NAVER API HUB 지역 검색 — 구현에 필요한 사실
제품 구분·구조적 제약(검색어당 5건 상한, 거리순 정렬 미지원, CORS 미지원, 고유 장소 ID 부재)과 공식
문서 링크는 [공통 명세 §4](./specification.md) 에 있다. 여기서는 **백엔드가 코드로 처리해야 하는 것만** 적는다.

| 다뤄야 할 것 | 처리 |
|---|---|
| 호출 규격 | `GET https://naverapihub.apigw.ntruss.com/search/v1/local?query=…&display=5` |
| 인증 헤더 | `X-NCP-APIGW-API-KEY-ID`(Client ID), `X-NCP-APIGW-API-KEY`(Client Secret) |
| `Accept` 헤더 | `application/json;charset=UTF-8` 로 **반드시 명시한다.** 빠지면 JSON 본문에 `text/plain` 을 붙여 보내 변환에 실패한다 |
| 응답 필드 | `title, category, address, roadAddress, telephone, mapx, mapy, link` |
| 좌표 변환 | `mapx`(경도)/`mapy`(위도) 는 WGS84 정수형(10^7 배율) → **`/10,000,000` 변환 후** 저장·응답 (API 명세 §2.4.1) |
| 호출 경로 | 브라우저가 직접 호출할 수 없으므로 **백엔드 프록시가 유일한 경로**다 (API 명세 §2.4.1) |
| 키 보관 | 지역 검색 Client ID/Secret 은 서버 환경변수로만 두고 응답에 싣지 않는다 |
| `telephone` | 문서상 값을 반환하지 않는 하위 호환용 필드다. 빈 값이 올 수 있다 |
| 건수 부족 | 후보 풀이 요청 건수에 못 미쳐도 **오류가 아니다.** 확보된 만큼 반환한다 |

- `title` 에는 검색어 강조용 HTML 태그가 섞여 오므로 제거 후 저장한다.
- 좌표 배율(10^7)은 실제 응답 표본으로 확인했다 (`mapy` `375026235` → 위도 37.5026235).
- 값이 없는 선택 필드(`link`, `telephone` 등)는 빈 문자열로 오므로 `null` 로 바꾼다. 좌표를 읽을 수
  없는 항목은 후보에서 뺀다.

## 2. 인증 및 인가

### 2.1 로그인

> **현재 구현은 임시 인메모리 로그인이다.** OAuth 로그인은 실제 키와 콜백 도메인이 갖춰지기
> 전까지 비활성 상태이며, 아래 OAuth·가입 내용은 구현 대상 명세다 (§6.1).
>
> - 로그인: `POST /api/auth/login` — JSON `{ "username", "password" }`. 성공 시 세션 쿠키를
>   발급하고 `MeResponse` 를 반환하며, 실패는 `401 UNAUTHENTICATED` 다 (API 명세 §3.3 의 공통 오류 형식).
> - 계정은 `local`·`dev` 프로파일에서만 등록되는 고정 3개(`user1`~`user3`, 비밀번호
>   `password`)다. 고정 비밀번호 계정을 운영 환경에 열지 않기 위해 프로파일을 제한한다.
>   OAuth 로그인을 구현한 뒤에도 개발·테스트용으로 남긴다.
> - 이 계정들은 기동 시 `users` 에 `provider = "local"`, `provider_id = username`,
>   `name = username` 으로 upsert 된다. 가입 절차를 거치지 않는다.
> - 로그인 수단이 둘이 되면서 principal 구현 타입도 둘이다. 컨트롤러는 수단이 아니라
>   `LoginUser`(사용자 PK 만 노출하는 인터페이스)에 의존한다.

**제공자**

- Spring Security OAuth2 Client 의 Authorization Code 플로우를 쓴다. 제공자 이름은 클라이언트
  등록 id(`registrationId`)이며, 그대로 `users.provider` 값이 된다.
  - 로그인 시작: `GET /oauth2/authorization/{provider}`
  - 콜백: `GET /login/oauth2/code/{provider}` (Spring Security 기본 처리)
- **제공자 응답을 공통 프로필로 바꾸는 곳은 `OAuthProvider` enum 하나다.** 항목마다 제공자 응답에서
  `OAuthProfile(provider, providerId, profileImageUrl)` 를 꺼내는 규칙을 갖는다.
  등록되지 않은 `registrationId` 로 들어온 로그인은 실패시킨다.

  | 제공자 | providerId | profileImageUrl | 비고 |
  |---|---|---|---|
  | `naver` | `response.id` | `response.profile_image` | 프로필이 `response` 로 감싸여 온다 |
  | `google` (추가 시) | `sub` | `picture` | OIDC. `OidcUserService` 경로로 들어온다 |
  | `kakao` (추가 시) | `id` | `kakao_account.profile.profile_image_url` | `id` 가 숫자라 문자열로 바꾼다 |

- OAuth2 경로(`OAuth2UserService`)와 OIDC 경로(`OidcUserService`) 모두 공통 프로필로 바꾼 뒤 같은
  계정 조회로 넘긴다. 어느 경로로 들어왔는지에 따라 계정 처리가 달라지지 않는다.
- **제공자를 더하는 일은 `application.yml` 의 클라이언트 등록과 `OAuthProvider` 항목 추가뿐이다.**
  스키마는 바뀌지 않는다.
- 제공자에게 요청하는 범위(scope)는 식별자·프로필 사진을 받는 최소한으로 둔다. **이메일·이름은
  요청하지 않는다** (공통 명세 §3.1). 네이버는 애플리케이션 설정에서 프로필 사진만 켠다. Google 은
  식별자(`sub`)가 `openid` 범위로 오므로 `openid`·`profile` 만 요청한다 — `profile` 을 빼면 사진이
  오지 않는다. 응답에 이름이 함께 오더라도 꺼내지 않는다.
- 제공자 식별자는 문자열로 저장한다. 제공자 응답에 식별자가 없으면 로그인을 실패시킨다.

**콜백 이후 — 기존 사용자와 새 사용자**

- 콜백에서 `provider + providerId` 로 `User` 를 찾는다.
- **있으면 로그인시킨다.** `profileImageUrl` 을 제공자 값으로 갱신하고, `name` 은 건드리지 않는다.
  로그인 이력을 남기고 프론트엔드의 돌아갈 주소로 보낸다.
- **없으면 계정을 만들지 않고 로그인시키지도 않는다.** 성공 처리(`AuthenticationSuccessHandler`)가
  인증 정보를 SecurityContext 와 세션에서 지우고, 공통 프로필과 **제안 닉네임**을 세션 속성
  **"가입 대기"** 로 남긴 뒤 프론트엔드의 가입 화면으로 보낸다.
  - 제안 닉네임은 `여행자_` 뒤에 임의의 숫자 6자리(`000000`~`999999`)를 붙여 만든다. 이미 쓰이는
    값이면 다시 뽑되 다섯 번까지만 뽑는다. 다섯 번 모두 겹치면 마지막 값을 그대로 제안한다 — 가입
    요청에서 `409` 로 걸러진다. 가입 대기를 만들 때 한 번 정해 두고 바꾸지 않는다.
  - 제안 닉네임이 비어 있는지 미리 보는 것은 편의일 뿐이다. 가입 요청 사이에 남이 먼저 쓰면
    가입 요청이 `409 NAME_TAKEN` 이 되며, 사용자가 고쳐서 다시 보낸다.
  - 가입 대기는 **만든 지 10분이 지나면 무효**다. 만든 시각을 함께 저장해 쓸 때 판정한다.
  - 가입 대기는 세션당 하나다. 다시 OAuth 로그인을 시작하면 이전 가입 대기를 버린다.
- **가입: `POST /api/auth/signup`** — 요청의 `name` 을 검증하고(§3.1), 가입 대기의 프로필로 `User` 를
  만든 뒤 로그인시킨다. 로그인 이력을 남기고 `MeResponse` 를 반환한다.
  - 가입 대기가 없거나 만료되었으면 `401 SIGNUP_EXPIRED` 다. 로그인 자체를 다시 해야 하는 상황이라
    `401` 이다.
  - 같은 `provider + providerId` 의 `User` 가 이미 있으면(다른 탭에서 먼저 가입한 경우) 새로 만들지
    않고 그 계정으로 로그인시킨다. 유니크 제약 위반을 오류로 돌려주지 않는다.
  - 성공하면 가입 대기를 지운다. 실패(닉네임 검증·중복)하면 가입 대기를 남겨 다시 시도할 수 있게 한다.
  - 로그인 처리(세션 id 교체·CSRF 토큰 폐기)는 임시 로그인과 같은 코드를 쓴다 (§5).
- **닉네임 변경: `PATCH /api/auth/me`** — 본인의 `name` 만 바꾼다. 검증은 가입과 같다 (§3.1).

**공통**

- 로그인 성공 시 세션 쿠키(`JSESSIONID`)를 발급한다. 프론트엔드는 이후 요청에
  `credentials: 'include'` 로 쿠키를 포함한다.
- 로그아웃: `POST /api/auth/logout` — 세션을 무효화한다.
- OAuth 실패(동의 거부·제공자 오류·등록되지 않은 제공자)는 프론트엔드 로그인 화면으로
  `?error=oauth_failed` 를 붙여 보낸다. 사유를 더 나누지 않는다.

**로그인 이력 기록** (공통 명세 §3.10)

- **로그인에 성공한 지점에서만 기록한다.** 인증 실패 경로에는 기록 코드를 두지 않는다.
  - 로컬 로그인(`POST /api/auth/login`): 인증 성공 뒤, 세션을 발급하기 전에 기록한다. 기록이
    실패하면 세션을 발급하지 않으며, 오류 응답은 API 명세 §3.3 의 공통 규칙을 따른다.
  - OAuth 기존 사용자: 콜백의 성공 처리에서 기록한다. 기록이 실패하면 로그인도 실패로 처리한다.
  - 가입(`POST /api/auth/signup`): `User` 생성과 같은 트랜잭션에서 기록한다. 기록이 실패하면
    계정도 만들어지지 않는다. 가입 대기만 만든 콜백은 기록하지 않는다.
- 접속 IP 는 `HttpServletRequest.remoteAddr` 다. 프록시 헤더 해석(§5)이 켜져 있어 프록시 뒤에서도
  원 클라이언트 주소가 된다. `X-Forwarded-For` 를 직접 읽지 않는다 — 신뢰할 프록시 판정이 이미
  Tomcat 에 있다.
- 브라우저 정보는 `User-Agent` 헤더 원문이다. 512자를 넘으면 앞에서부터 512자로 자르고, 헤더가
  없으면 `null` 이다. 서버에서 해석(브라우저·OS 추출)하지 않는다.

### 2.2 인가 정책

권한 규칙 자체는 [공통 명세 §2.6](./specification.md) 에 있다. 여기서는 **그 규칙을 코드에서
어떻게 판정하고 어떤 상태 코드로 응답하는지**를 정의한다.

#### 2.2.0 인증 관문

**로그인 여부는 필터체인(`SecurityConfig`)이 먼저 판정한다.** 비로그인에게 열린 경로만 나열하고
나머지는 모두 로그인을 요구한다 — 새 엔드포인트는 열어 두기로 정하지 않는 한 닫혀 있다.

| 비로그인에게 열린 경로 | 이유 |
|---|---|
| `GET /api/trips/**`, `GET /api/records/**` | 전체 공개 여행·기록 열람. 범위 판정은 §2.2.1 이 한다 |
| `GET /api/tags` | 볼 수 있는 기록의 태그만 나온다 (API 명세 §2.8.1) |
| `GET /api/files/**` | 사진 서빙. 소속 여행의 판정을 거친다 (API 명세 §2.5.3) |
| `GET /api/auth/session`, `POST /api/auth/login` | 로그인 전 부팅·로그인 자체 |
| `GET /oauth2/authorization/**`, `GET /login/oauth2/code/**` | OAuth 로그인 시작·콜백 |
| `POST /api/auth/signup` | 가입 대기는 로그인 전 상태다. 가입 대기가 있는지는 컨트롤러가 판정한다 (§2.1) |
| 관리 포트의 `health`·`prometheus` | 수집기·상태 확인은 로그인하지 않는다. 외부 차단은 관리 포트 비공개가 맡는다 (§5) |

- 필터에서 막힌 요청도 `401 UNAUTHENTICATED` 를 API 명세 §3.3 의 오류 형식으로 받는다. 비로그인 요청은
  매핑되지 않은 주소도 `404` 가 아니라 `401` 이다 — 판정이 주소 매핑보다 먼저다.
- 컨트롤러의 `requireLogin` 은 그대로 둔다. 사용자 id 를 얻는 경로이자 두 번째 관문이다.
- CORS 사전 요청(preflight)은 쿠키 없이 오므로 필터체인이 CORS 를 먼저 처리한다.

#### 2.2.1 판정식

**모든 열람 판정은 여행(`Trip`)에 대해 수행한다.** `TripRecord` 는 공개 범위를 갖지 않으므로
기록의 열람 가능 여부는 `record.trip` 의 판정 결과와 같다.

```
canView(trip)   := requester == trip.owner
                || trip.visibility == PUBLIC
                || (trip.visibility == GROUP && requester ∈ members(shares(trip)))

canView(record) := canView(record.trip)
canEdit(trip)   := requester == trip.owner
canEdit(record) := requester == record.trip.owner
```

- **편집 권한은 `Trip.owner` 로만 판단하며 그룹과 무관하다.** 멤버라는 사실은 조회 권한만 준다.
- `canEdit` 는 사진 업로드·삭제, 커버 사진 지정, 기록 등록·이동에도 그대로 적용된다.
- **기록 등록도 `canEdit(trip)` 를 통과해야 한다.** 등록·이동의 대상 여행은 실패 시 상태 코드가
  다르다 (§2.2.2).

#### 2.2.2 실패 시 상태 코드

| 상황 | 상태 | code |
|---|---|---|
| 비로그인으로 인증 필요 엔드포인트 접근 | 401 | `UNAUTHENTICATED` |
| `canView` 실패 — 여행 | 404 | `TRIP_NOT_FOUND` |
| `canView` 실패 — 기록 (소속 여행을 못 보는 경우 포함) | 404 | `RECORD_NOT_FOUND` |
| `canView` 는 통과하나 `canEdit` 실패 | 403 | `FORBIDDEN` |
| 받은 본인이 아닌 사람의 초대 수락·거절 | 404 | `INVITE_NOT_FOUND` |

- **열람 권한이 없으면 `403` 이 아니라 `404` 다.** 응답 본문이 실제로 없는 것과 구분되지 않아야
  하며, 메시지 문구도 같아야 한다 (API 명세 §3.3).
- **소속 여행을 볼 수 없어 가려지는 기록은 `TRIP_NOT_FOUND` 가 아니라 `RECORD_NOT_FOUND` 다.**
  `TRIP_NOT_FOUND` 를 내려주면 "기록은 있는데 여행을 못 본다"는 사실이 새어 나간다.
- **열람은 되는데 수정 권한이 없는 경우만 `403` 이다.** 이때는 존재가 이미 열람으로 드러나 있으므로
  숨길 이유가 없다.
- **그룹은 예외로, 존재를 숨기지 않는다.** 멤버가 아닌 사용자의 그룹 상세 조회·탈퇴는 `404` 가
  아니라 `403 FORBIDDEN` 이다. 여행·기록은 가려진 대상의 내용이 곧 사생활이지만, 그룹에서
  가려지는 것은 멤버 명단이고 그것은 `403` 에서도 드러나지 않는다. 조회와 수정이 같은 코드로
  답하므로 id 를 훑어 얻을 수 있는 정보도 늘지 않는다.
- **단, 여행 공유 요청에 담긴 그룹 id 는 그대로 `404 GROUP_NOT_FOUND` 다** (아래 하위 리소스 항목).
  이 경로는 그룹 화면을 거치지 않고 임의의 id 를 넣어볼 수 있어 판단이 다르다.
- **같은 이유로, 기록 등록·이동의 대상 여행은 소유하지 않으면 볼 수 있어도 `404 TRIP_NOT_FOUND` 다.**
  요청 본문에 임의의 여행 id 를 넣어볼 수 있는 경로라 `403` 으로 답하면 그 여행의 존재가 드러난다.
- 같은 은닉 원칙이 **하위 리소스 ID**에도 적용된다. 남의 여행 사진 ID를 커버로 지정하려 하면
  `404 PHOTO_NOT_FOUND`, 속하지 않은 그룹 ID로 공유하려 하면 `404 GROUP_NOT_FOUND` 다.

## 3. 도메인 모델

아래는 엔티티의 **구조**(필드와 관계)만 보여 준다. 길이·범위·필수 여부 같은 **값 규칙은 §3.1이
유일한 출처**이며 주석에 중복해 적지 않는다.

```
User
  id: Long (PK)
  provider: String              // OAuth registrationId ("naver" 등), 임시 로그인은 "local"
  providerId: String            // 제공자 안의 사용자 식별자 (§2.1 표)
  name: String                  // 닉네임. 사용자가 정하며 제공자 값으로 덮어쓰지 않는다
  profileImageUrl: String?      // 로그인할 때마다 제공자 값으로 갱신
  createdAt: Instant

Trip                            // 여행 — 여행 기록의 상위 그룹이자 공유의 단위
  id: Long (PK)
  owner: User (FK)              // 여행 소유자 = 하위 기록 전부의 작성자. 편집 권한의 유일한 근거
  name: String                  // 여행 이름
  startDate: LocalDate          // 시작일
  endDate: LocalDate            // 종료일
  headcount: Int                // 인원 (사람 수. 계정과 무관)
  budget: Long?                 // 총 예산 (원)
  memo: String?                 // 여행 설명
  coverPhoto: Photo?            // 커버 사진 (하위 기록의 사진 중 하나)
  visibility: TripVisibility    // PRIVATE | GROUP | PUBLIC
  createdAt: Instant
  updatedAt: Instant
  deletedAt: Instant?           // soft delete (§3.2)

TripRecord                     // 여행 기록 — 반드시 여행 하나에 속한다
  id: Long (PK)
  trip: Trip (FK, NOT NULL)     // 소속 여행. 작성자·공개 범위의 유일한 출처
  name: String                  // 장소명 (외부 검색 결과 스냅샷)
  category: Category            // SIGHT | SHOPPING | FOOD
  address: String               // 지번 주소 (스냅샷)
  roadAddress: String?          // 도로명 주소 (스냅샷)
  externalLink: String?         // 지역 검색 결과 원본 링크. 안정적 장소 ID가 없어 참고용
  latitude: Double              // WGS84
  longitude: Double             // WGS84
  rating: Double                // 작성자 본인의 평점
  memo: String?                 // 자유 텍스트 설명
  createdAt: Instant
  updatedAt: Instant
  deletedAt: Instant?           // soft delete (§3.2)
  // author 컬럼은 두지 않는다 — 기록을 만들 수 있는 사람이 여행 소유자뿐이라 trip.owner 와 항상 같다
  // visibility 컬럼도 두지 않는다 — 공개 범위는 trip.visibility 하나뿐이다

Tag
  id: Long (PK)
  name: String (unique)

TripRecordTag                  // TripRecord - Tag 다대다 조인
  record: TripRecord (FK)
  tag: Tag (FK)

Photo                           // 사진 메타데이터. 바이너리는 PhotoData 에 따로 둔다 (§4.1)
  id: UUID (PK)                 // 애플리케이션이 생성하는 무작위 UUID(v4). 사진 URL 에 그대로 실린다 (§4.1)
  record: TripRecord (FK)
  originalFileName: String
  contentType: String
  fileSizeBytes: Long
  createdAt: Instant
  // uploader 컬럼은 두지 않는다 — 사진을 올릴 수 있는 사람이 작성자뿐이라 record.trip.owner 와 항상 같다

PhotoData                       // 사진 바이너리 (테이블명 photo_data)
  photo: Photo (PK·FK)          // Photo 와 1:1. id 를 공유한다
  data: ByteArray               // BYTEA

Group                           // 조회 전용 공유 대상 목록 (테이블명 share_group, §3.1)
  id: Long (PK)
  owner: User (FK)
  name: String                  // 그룹 이름
  memo: String?                 // 그룹 설명. 소유자와 멤버 모두에게 보인다
  createdAt: Instant
  deletedAt: Instant?           // soft delete (§3.2)

GroupMember
  id: Long (PK)
  group: Group (FK)
  user: User (FK)
  joinedAt: Instant
  // unique(group_id, user_id)

GroupInvite                     // 소유자가 특정 사용자 앞으로 보낸 가입 요청 (공통 명세 §3.7)
  id: Long (PK)
  group: Group (FK)
  invitee: User (FK)            // 초대받은 사람. 가입자만 지정할 수 있다
  invitedBy: User (FK)          // 보낸 사람 = 초대 시점의 그룹 소유자
  createdAt: Instant
  // unique(group_id, invitee_id) — 같은 사람에게 같은 그룹의 대기 초대는 1건
  // 토큰·만료 컬럼은 두지 않는다 — 초대가 서비스 밖으로 나가지 않아 추측 대상도, 수명도 없다
  // 상태 컬럼을 두지 않는다 — 끝난 초대는 행을 지우고 InviteHistory 로 옮긴다 (§3.2)

InviteHistory                   // 끝난 초대의 기록 (공통 명세 §3.7). append-only
  id: Long (PK)
  groupId: Long                 // FK 를 걸지 않는다 — 그룹이 지워져도 이력은 남아야 한다
  groupName: String             // 삭제된 그룹도 이름을 보여주기 위한 스냅샷
  invitee: User (FK)            // 초대받았던 사람
  invitedBy: User (FK)          // 보냈던 사람
  outcome: InviteOutcome        // ACCEPTED | REJECTED | REVOKED | GROUP_DELETED
  invitedAt: Instant            // 원래 초대의 createdAt 을 그대로 옮긴다
  resolvedAt: Instant           // 끝난 시각
  // unique 제약을 두지 않는다 — 같은 상대에게 다시 초대해 다시 끝나면 항목이 하나 더 쌓인다

LoginHistory                    // 성공한 로그인의 기록 (공통 명세 §3.10). append-only
  id: Long (PK)
  user: User (FK)               // 로그인한 계정
  ipAddress: String             // 접속 IP
  userAgent: String?            // User-Agent 원문
  loggedInAt: Instant           // 로그인 시각
  // 로그인 수단 컬럼을 두지 않는다 — 계정당 수단이 하나라 user.provider 와 항상 같다
  // 결과 컬럼을 두지 않는다 — 성공만 기록한다

// 서비스 통계 (공통 명세 §3.11). 엔티티 없이 배치만 쓴다. 모두 aggregatedAt(마지막으로 집계한 시각)을 갖는다.
// 사용자를 가리키는 컬럼을 두지 않는다 — 누구의 것인지 남지 않아야 기한 없이 보관할 수 있다.

LoginDailyStats                 // login_daily_stats
  statDate: LocalDate (PK)      // 한국 시간 기준 날짜
  loginCount: Long              // 그날의 로그인 횟수
  uniqueUserCount: Long         // 그날 로그인한 사용자 수

LoginPeriodStats                // login_period_stats
  periodType: String (PK)       // WEEK | MONTH
  periodStart: LocalDate (PK)   // 주는 그 주 월요일, 월은 그달 1일
  uniqueUserCount: Long         // 그 기간에 로그인한 사용자 수

UserDailyStats                  // user_daily_stats
  statDate: LocalDate (PK)
  newCount: Long                // 그날 가입한 수

TripDailyStats                  // trip_daily_stats
  statDate: LocalDate (PK)
  createdCount: Long
  deletedCount: Long

RecordDailyStats                // record_daily_stats
  statDate: LocalDate (PK)
  createdCount: Long
  deletedCount: Long            // 여행 삭제로 함께 삭제된 기록 포함

GroupDailyStats                 // group_daily_stats
  statDate: LocalDate (PK)
  createdCount: Long
  deletedCount: Long
  memberCount: Long             // 집계 시점 값. 원본(group_member)이 물리 삭제라 다시 구할 수 없다
  inviteSentCount: Long
  inviteAcceptedCount: Long
  inviteRejectedCount: Long
  inviteRevokedCount: Long

TripShare                       // trip.visibility=GROUP 일 때만 사용
  id: Long (PK)
  trip: Trip (FK)
  group: Group (FK)
  // unique(trip_id, group_id)
```

`Trip.coverPhoto` 와 `Photo.record` 는 `Trip → TripRecord → Photo → Trip` 순환 참조를 만든다.
`cover_photo_id` 컬럼은 `trips` 에 두되 **외래키는 선언하지 않는다.** 마이그레이션 도구가 없던 때
순환 참조 FK 를 재실행 가능한 스크립트로 선언할 수 없어 생긴 결정이며, Flyway 를 도입한 지금은
되돌릴 수 있다 (§6.1).

따라서 **사진이 지워질 때 커버 지정을 푸는 것은 애플리케이션의 책임**이다. 사진이 사라져도 여행은
남아야 하므로 이 처리를 빠뜨리면 여행 조회가 깨진다. FK 를 복원하면 사진 삭제 경로는
`ON DELETE SET NULL` 로 대신할 수 있다.

### 3.1 제약 조건

**사용자**

- `(provider, provider_id)` 는 유니크다. 계정 하나에 외부 계정 하나라는 규칙(공통 명세 §3.1)을
  DB 가 보장한다.
- `User.name`(닉네임)의 형식·금지어 규칙은 공통 명세 §3.1 이다. 검증 순서는 다음과 같다.
  1. 앞뒤 공백을 지우고 유니코드 NFC 로 정규화한다. 저장도 이 값으로 한다.
  2. 형식(길이·허용 문자)을 어기면 `400 VALIDATION_ERROR` 다.
  3. 금지어와 대소문자 무시로 일치하면 `400 VALIDATION_ERROR` 다. 금지어 목록은 코드 한 곳에 둔다.
  4. 다른 사용자가 쓰는 닉네임과 대소문자 무시로 같으면 `409 NAME_TAKEN` 이다.
- **유일성은 DB 가 판정한다.** `lower(name)` 에 유니크 인덱스를 두고, 미리 조회해 보는 검사는
  빠른 실패용일 뿐이다. 동시에 같은 닉네임으로 가입·변경하면 제약 위반이 나며, 이것도
  `409 NAME_TAKEN` 으로 바꾼다.
- 본인의 현재 닉네임과 같은 값(대소문자만 다른 경우 포함)으로 바꾸는 것은 허용한다.

**여행**

- `Trip.name` 은 필수이며 50자 이하다. 공백만으로 이루어진 이름은 `400 VALIDATION_ERROR` 다.
- `Trip.startDate`, `endDate` 는 필수이며 **`endDate >= startDate`** 여야 한다. 위반 시
  `400 VALIDATION_ERROR` 이고, DB `CHECK` 제약으로도 막는다. 같은 날이면 당일치기다.
  **미래 날짜를 막지 않는다** — 여행 중 등록이 정상 사용이고, 서버가 "다녀온 뒤"를 판정할 수 없다.
- `Trip.headcount` 는 필수이며 **1 이상의 정수**다. 본인을 포함한 사람 수이고 계정·공유 그룹과
  무관하다. 상한은 두지 않는다.
- `Trip.budget` 은 선택이며 **0 이상의 정수**(원 단위)다. 통화 컬럼은 두지 않는다 — KRW 고정이다.
  `null` 은 "예산 정보 없음"이고 `0` 과 구분된다.
- `Trip.visibility` 는 `PRIVATE` / `GROUP` / `PUBLIC` 중 하나이며 **기본값은 `PRIVATE`** 이다.
  요청에서 생략되면 `PRIVATE` 로 저장한다. **공개 범위 컬럼은 이 한 곳에만 존재한다.**
- `Trip.coverPhoto` 는 선택이며, **그 여행의 하위 기록에 속한 사진만** 지정할 수 있다.
  다른 여행의 사진 ID가 오면 `404 PHOTO_NOT_FOUND` 다 (존재 은닉).
- **여행당 기록 수에 제한을 두지 않는다.** 기록이 0건인 여행도 유효하다.

**여행 기록**

- `TripRecord.trip` 은 **NOT NULL** 이다. 여행에 속하지 않는 기록은 저장될 수 없다.
- **`TripRecord` 에는 `author` 와 `visibility` 컬럼이 없다.** 둘 다 `trip` 에서 파생되며,
  같은 사실을 두 곳에 적으면 어긋나는 순간 어느 쪽이 맞는지 알 수 없기 때문이다
  (`deletedAt` 단일화와 같은 이유, §3.2). 응답 DTO 의 `author` 필드는 `trip.owner` 로 채운다.
- 기록을 다른 여행으로 옮기는 것은 **`trip` FK 변경**으로 처리한다. 옮길 대상 여행은
  **요청자가 소유한 여행**이어야 한다 (실패 응답은 §2.2.2). 옮기는 순간 그 기록의
  공개 범위는 새 여행의 것이 된다.
- `TripRecord.category` 는 `SIGHT`(관광지) / `SHOPPING`(쇼핑) / `FOOD`(맛집) 중 하나만 허용한다.
- `TripRecord.latitude`, `longitude` 는 필수이며 WGS84 위경도(소수점 6자리 이상)로 저장한다.
  지역 검색 결과의 `mapx`/`mapy` 는 저장 전 변환한다 (§1.3).
- `TripRecord.rating` 은 **0.5 ~ 5.0 범위의 0.5 단위 값**만 허용하며 **필수**다. `Double` 로
  저장하는데 0.5 배수는 이진 부동소수점으로 정확히 표현되므로 비교에 오차가 생기지 않는다.
  요청 DTO 의 Bean Validation 과 DB `CHECK` 제약 양쪽에서 검증한다.
- **평균 평점을 집계하지 않는다.** 기록마다 평점이 하나뿐이라 집계 대상이 없다. 이전 판의
  `rating.average` / `rating.count` 응답 필드는 폐기되었다.
- **같은 장소의 기록이 여러 건 존재하는 것이 정상이다.** 같은 여행 안에서도 마찬가지이며,
  중복 판정도 중복 방지 제약도 두지 않는다.
- **여행 단위의 평균 평점도 집계하지 않는다.** 한 여행 안의 관광지와 맛집 평점을 평균 내는 것은
  의미가 없다.

**그룹·공유**

- `Group` 은 SQL 예약어라 테이블명을 `share_group` 으로 둔다 (엔티티 클래스명은 `GroupEntity`).
- `Group.memo` 는 선택이며 길이 상한은 공통 명세 §3.6이 정한다. 공백만 들어오면 `null` 로 저장해
  "메모 없음" 과 같은 값으로 만든다. **요청자에 따라 달라지지 않는다** —
  그룹을 조회할 수 있는 사람은 소유자와 멤버뿐이고(API 명세 §2.6.3) 둘 다 메모를 볼 수 있으므로, DTO 에
  요청자별 분기를 두지 않는다.
- **그룹당 멤버는 소유자 포함 최대 5명**이다. 애플리케이션 레벨에서 검증하며, 초과 시
  `409 GROUP_MEMBER_LIMIT_EXCEEDED` 다. 그룹 생성 시 소유자를 `GroupMember` 로 함께 입력한다.
- `GroupInvite` 는 `unique(group_id, invitee_id)` 다. 대기 중인 초대가 있는 상대를 다시
  초대해도 행이 늘지 않고 기존 초대가 그대로 유지된다 (멱등, API 명세 §2.7.2).
- **초대 대상은 가입자만이다.** 요청의 이메일과 일치하는 `User` 가 없으면 `404 USER_NOT_FOUND`
  이며, 이메일 비교는 대소문자를 구분하지 않는다. 이미 `GroupMember` 인 사용자를 초대하면
  `409 ALREADY_MEMBER` 다.
- **정원 판정은 수락 시점에만 한다.** 대기 중인 `GroupInvite` 는 정원에 포함되지 않으므로,
  정원이 찬 뒤의 수락은 `409 GROUP_MEMBER_LIMIT_EXCEEDED` 로 거부된다. 이때 **초대 행은 지우지
  않는다** — 자리가 나면 같은 초대로 다시 수락할 수 있어야 한다 (공통 명세 §3.7).
- **사용자당 그룹 수 상한**(공통 명세 §3.6)은 그룹 생성 시 삭제되지 않은 소유 그룹 수(`share_group.owner_id`)로,
  초대 수락 시 소유하지 않은 멤버 행 수로 판정한다. 초과 시 둘 다 `409 GROUP_LIMIT_EXCEEDED` 이며,
  수락이 거부되면 정원 초과와 같이 초대 행과 이력을 건드리지 않는다. **잠금은 걸지 않는다** —
  동시 요청으로 한 건 넘칠 수 있으나, 상한의 목적이 목록 크기를 묶는 것이라 정원처럼 정확히
  지켜야 할 약속이 아니다.
- `TripShare` 는 `Trip.visibility` 가 `GROUP` 일 때만 의미가 있다. `PRIVATE`/`PUBLIC` 으로
  바꿀 때 기존 공유 행은 삭제한다 — 남겨 두면 나중에 `GROUP` 으로 되돌렸을 때 의도치 않은
  공유가 되살아난다.
- 공유할 수 있는 그룹(공통 명세 §3.5) 밖의 그룹 ID가 요청에 담기면 `404 GROUP_NOT_FOUND` 다
  (존재 은닉).
- **멤버 탈퇴·제외 시 `trip.owner` 가 그 사용자인 `TripShare` 중 그 그룹의 행을 같은 트랜잭션에서
  지운다** (공통 명세 §3.6). 여행의 `visibility` 는 `GROUP` 그대로 두며, 남은 공유 행이 없으면
  `PRIVATE` 과 같이 판정된다.
- **기록 단위의 공유 관계는 존재하지 않는다.** 이전 판의 `VisitRecordShare` 는 폐기되었다.
- **`InviteHistory` 는 초대 행을 지우는 모든 경로에서, 지우는 것과 같은 트랜잭션 안에 남긴다.**
  경로는 넷이다 — 수락·거절·취소(철회)·그룹 삭제. 한 곳만 빠뜨려도 이력이 조용히 비고,
  그런 누락은 조회 시점에 드러나지 않는다.
- **`InviteHistory.groupId` 에는 외래키를 걸지 않는다.** 그룹을 물리 삭제하던 때 이력까지 함께
  지워지거나 삭제가 막히지 않도록 정한 것이며, 그룹이 soft delete 로 바뀐 지금은 걸 수 있다. 대신 `groupName` 스냅샷을 함께 저장해,
  그룹이 사라진 뒤에도 무엇에 대한 초대였는지 답할 수 있게 한다 (공통 명세 §3.7).
- **정원 초과로 수락이 거부된 경우는 이력을 남기지 않는다.** 초대 행이 그대로 남아 있으므로
  끝난 것이 아니다 (API 명세 §2.7.7).
- `Tag` 는 이름 중복 없이 재사용되며, 존재하지 않는 태그명이 등록 요청에 포함되면 서버가 생성한다.

**로그인 이력**

- 테이블명은 `login_history` 다.
- `ipAddress` 는 필수이며 45자 이하다 (IPv6 표기 최대 길이).
- `userAgent` 는 선택이며 512자 이하다. 넘는 값은 저장 전에 자른다 (§2.1).
- 조회는 항상 본인 것을 최신순으로 읽으므로 `login_history(user_id, logged_in_at DESC)` 인덱스를 둔다.
- 보관 기간 정리(§3.2)는 시각만으로 지울 행을 찾으므로 `login_history(logged_in_at)` 인덱스를 따로 둔다.
  위 인덱스는 `user_id` 가 선두라 이 조건에 쓰이지 않는다. 일별 통계 집계도 같은 인덱스를 쓴다.

**서비스 통계**

- 테이블은 `login_daily_stats`, `login_period_stats`, `user_daily_stats`, `trip_daily_stats`,
  `record_daily_stats`, `group_daily_stats` 다. 이름은 `<대상>_daily_stats` 로 맞추고, 일별이 아닌 것만
  단위를 이름에 쓴다. PK 는 `stat_date`(기간 통계는 `period_type, period_start`)이며 날짜당 한 행이다.
- 엔티티가 없다. `external-api` 는 이 테이블들을 읽지도 쓰지도 않으며, `batch` 의 `dailyStatsJob` 만
  SQL 로 쓴다. DDL 은 스키마의 단일 출처인 `external-api` 의 Flyway 마이그레이션에 둔다.
- **`dailyStatsJob` 은 파라미터 없이 한국 시간 기준 어제 하루만 집계한다.** 범위는 어제 한국 시간 0시 이상,
  오늘 0시 미만이다. 대상마다 스텝 하나를 둔다 — 로그인 일별 → 로그인 기간 → 사용자 → 여행 → 기록 → 그룹.
- **로그인 기간 스텝은 어제가 일요일이면 그 주(월~일)를, 그달 말일이면 그 달을 집계한다.** 둘 다 아니면
  아무것도 쓰지 않는다. `period_type` 은 `WEEK` / `MONTH` 이며 `CHECK` 제약으로 막는다.
- **같은 날 다시 돌리면 덮어쓴다.** 테이블마다 `INSERT … ON CONFLICT … DO UPDATE` 한 문장이다.
  지난 날짜를 지정하는 수단은 두지 않는다 (공통 명세 §3.11).
- 생성 수는 `created_at`, 삭제 수는 `deleted_at` 이 그날 범위에 드는 행이다. 배치는 엔티티를 거치지
  않으므로 삭제된 행도 그대로 읽힌다.
- 그룹 스텝의 값:
  - `member_count` — 집계 시점에 `deleted_at IS NULL` 인 그룹의 `group_member` 행 수. 다른 값과 달리 어제의
    범위가 아니라 실행 시각의 값이다.
  - `invite_sent_count` — `invited_at`(`invite_history`)과 `created_at`(`group_invite`)이 그날인 행의 합.
    끝난 초대는 이력으로, 대기 중인 초대는 초대 행으로 남아 있기 때문이다.
  - `invite_accepted_count` · `invite_rejected_count` · `invite_revoked_count` — `invite_history.resolved_at` 이
    그날이고 `outcome` 이 각각 `ACCEPTED` / `REJECTED` / `REVOKED` 인 행 수. `GROUP_DELETED` 는 세지 않는다.
- 실행 순서: 매일 통계 잡을 먼저, 정리 잡(§3.2)을 다음에 돌린다. 어제 날짜는 보관 기간 안이라
  순서가 바뀌어도 값이 틀리지는 않지만, 통계가 원본보다 늦게 만들어질 일이 없게 맞춘다.

### 3.2 삭제 정책 (soft delete)
`Trip`, `TripRecord`, `Group` 은 물리 삭제하지 않고 `deletedAt` 에 삭제 시각을 기록한다. 통계·이력
보존과 오삭제 복구를 위해서다.

- **살아 있는 행의 기준은 `deletedAt IS NULL` 하나뿐이다.** `isDeleted` / `deleteYn` 같은 별도
  플래그는 두지 않는다. 같은 사실을 두 컬럼에 적으면 둘이 어긋나는 순간 어느 쪽이 맞는지 알 수
  없고, `deletedAt` 만으로 "삭제 여부"와 "삭제 시각"을 모두 답할 수 있다.
- 조회 제외는 엔티티의 `@SQLRestriction("deleted_at is null")` 이 처리한다. 삭제된 것은 `404` 다.
- **여행을 삭제하면 하위 기록도 같은 시각으로 함께 soft delete 한다.** 기록은 여행 없이 존재할 수
  없으므로(§3.1) 남겨 둘 자리가 없다. 하위 기록을 살려 두고 조회 시 `trip.deletedAt IS NULL` 로
  거르는 방식도 가능하지만, `@SQLRestriction` 은 조인 대상의 조건까지 걸어 주지 않아 누락 지점이
  생기기 쉽다 — 누락은 곧 정보 유출이므로 명시적 전파를 택한다.
- 기록 하나만 삭제하는 것은 여행에 영향을 주지 않는다. 마지막 기록을 지워 기록이 0건이 되어도
  여행은 그대로 남는다. **다만 그 기록의 사진이 커버였다면 커버 지정은 해제된다** — 커버는
  "그 여행의 하위 기록에 속한 사진" 이어야 하고(§3.1), 지워진 기록의 사진이 여행을 계속
  대표하게 두면 그 불변식이 깨진다.
- **기록·여행을 soft delete 하면 하위 사진의 바이너리(`photo_data` 행)를 같은 트랜잭션에서 물리 삭제한다**
  (공통 명세 §3.9). 사진 메타데이터(`photos` 행)는 기록과 함께 남는다. 바이너리 삭제는 기록 조회의
  삭제 필터(`@SQLRestriction`)를 거치지 않도록 네이티브 SQL 로 한다 — 기록을 먼저 soft delete 한 뒤에도
  지울 대상을 찾을 수 있어야 한다.
- `Photo` 는 바이너리(`PhotoData`)를 함께 지워야 하므로 soft delete 대상이 아니며 물리 삭제한다.
  삭제된 사진을 커버로 쓰던 여행은 `cover_photo_id` 가 `NULL` 이 된다. DB 제약이 아니라
  애플리케이션이 지우는 시점에 해제한다 (§3).
- **`GroupMember`, `GroupInvite`, `TripShare` 는 물리 삭제한다.** 공유 해제와
  그룹 탈퇴는 즉시 조회 권한을 없애야 하는 동작이라, 남아 있는 행이 권한 판정에 끼어들 여지를
  만들지 않는다. `Group` 만 soft delete 하는 것은 그룹 생성·삭제 수를 통계(공통 명세 §3.11)로 다시 셀 수
  있게 하기 위해서이며, 삭제된 그룹에는 멤버도 공유도 남지 않으므로 권한 판정에 끼어들지 않는다.
- 삭제된 그룹은 소유 그룹 수 상한(§3.1)에 세지 않고, 초대 이력에서는 사라진 그룹으로 취급한다.
- **초대는 수락·거절·철회·그룹 삭제 어느 쪽으로 끝나든 `group_invite` 행을 지우고, 같은 트랜잭션에서
  `InviteHistory` 를 남긴다** (공통 명세 §3.7). 상태 컬럼을 달아 같은 행에 두지 않는 이유는
  `unique(group_id, invitee_id)` 때문이다 — 끝난 초대가 그 자리에 남아 있으면 같은 상대를
  다시 초대할 수 없다. 부분 유니크 인덱스로 우회할 수는 있으나, 그러면 대기 초대를 읽는 모든 쿼리에
  상태 조건이 붙는다.
- **`InviteHistory` 는 지우지 않는다.** soft delete 대상도 아니다. 보관 기간을 두지 않으며,
  사용자 탈퇴 시의 처리는 탈퇴 자체와 함께 범위 밖이다 (공통 명세 §3.1).
- **`LoginHistory` 는 `loggedInAt` 이 보관 기간(공통 명세 §3.10)을 넘긴 행을 물리 삭제한다.**
  soft delete 대상이 아니다 — 보관 기간이 끝난 개인정보를 지운 것으로 표시만 해 두는 것은
  지우지 않은 것과 같다. 조회 API 는 보관 기간으로 거르지 않는다 — 기간 판정을 두 곳에 두지 않는다.
  - 삭제는 `batch` 모듈의 `loginHistoryCleanupJob` 이 맡는다. 배치 애플리케이션은 잡을 한 번
    돌리고 종료하며, **하루 한 번 외부 스케줄러(cron 등)가 실행한다.** 스케줄러는 이 저장소 밖에 둔다.
  - 기준 시각은 "실행 시각 − 보관 기간" 이고 **그보다 이전인 행만** 지운다. 경계 시각의 행은 남는다.
  - 같은 날 두 번 돌려도 결과가 같아 재시작 정보가 필요 없다. 그래서 배치 메타데이터 테이블
    (`BATCH_*`) 없이 도는 저장소(`ResourcelessJobRepository`)를 쓴다.
  - 보관 기간 값은 배치 설정(`app.login-history.retention-days`)에 있다.
- **서비스 통계 테이블(§3.1)은 지우지 않는다.** 누구의 것인지 남지 않는 합계라 보관 기간을 두지 않는다
  (공통 명세 §3.11).
- 그룹 삭제 시 그룹은 soft delete 하고, 그 그룹의 `GroupMember`, `GroupInvite`, `TripShare` 행은 지운다.
  이때 **대기 중이던 초대는 `GROUP_DELETED` 로 이력에 남긴다** — 받은 사람 쪽에서 보면
  초대가 이유 없이 사라지는 일이라 그 이유를 남겨 둘 자리가 필요하다.
  그 그룹으로만 공유되던 여행은 실질적으로 비공개가 되며, 여행과 기록 자체는 삭제되지 않는다.
- `updatedAt` 은 마지막 수정 시각이며 soft delete 도 수정으로 보아 함께 갱신된다.

## 4. 사진 저장소 설계

### 4.1 현재 단계 — 데이터베이스
- 사진 바이너리는 **`photo_data` 테이블의 `BYTEA` 컬럼**에 저장한다. 메타데이터(`photos`)와
  테이블을 나눈다.
  - 기록 목록·여행 커버처럼 `Photo` 를 읽는 경로가 많은데, 바이너리가 같은 행에 있으면 그때마다
    수 MB 가 함께 읽힌다. Hibernate 의 컬럼 단위 지연 로딩은 바이트코드 enhancement 없이는
    동작하지 않으므로 테이블을 나누는 것으로 막는다.
  - `@Lob` 을 쓰지 않는다. PostgreSQL 에서 `@Lob ByteArray` 는 `oid`(large object)로 매핑되어
    행을 지워도 본체가 남는다.
- **사진 id 는 무작위 UUID(v4)다.** 서빙은 소속 여행의 공개 범위를 판정하지만(API 명세 §2.5.3), 사진
  URL 에 id 가 그대로 실리므로 순번 id 면 공개 사진을 차례로 넣어 보는 것만으로 훑을 수 있다. 시각이
  앞에 오는 v7 은 추측의 단서를 늘리므로 쓰지 않는다. 사진 외 엔티티의 id 는 그대로 `Long` 이다.
- **사진 서빙도 여행·기록과 같은 판정식(§2.2.1)을 조회 조건으로 건다.** 사진 → 기록 → 여행을
  조인해 판정하며, 볼 수 없으면 없는 사진과 같은 `404 PHOTO_NOT_FOUND` 다.
- 저장·조회·삭제는 `PhotoDatabaseService` 가 맡는다. 구현이 하나뿐이므로 인터페이스를 두지 않는다.
- 업로드 한 장이 통째로 메모리에 올라가고, DB 백업 크기가 사진 용량만큼 커진다. 파일당 상한
  (공통 명세 §6.3) 안에서는 감당할 수 있는 비용으로 본다.

### 4.2 향후 전환 — 오브젝트 스토리지(AWS S3)
- 현재는 S3 를 고려하지 않는다. 저장 추상화도 전환 시점에 도입한다 — 구현이 하나인 지금
  인터페이스를 두면 위임만 하는 계층이 된다.
- API 응답의 `photos[].url` 은 저장 방식과 무관하게 항상 접근 가능한 URL 이므로, 전환해도
  프론트엔드는 바뀌지 않는다.
- S3 로 직접 내려보내면 지금의 서버 측 공개 범위 판정(§4.1)을 거치지 않게 된다. 전환 시에는
  판정을 통과한 요청에만 **서명 URL(pre-signed URL)** 을 짧은 수명으로 발급해 같은 보장을 유지한다.

## 5. 비기능 요구사항

세션 쿠키·CSRF·CORS의 **원칙**은 [공통 명세 §6.1](./specification.md) 에 있다. 여기서는
백엔드가 지켜야 할 구현 수단과, 공통 명세에 없는 서버 측 규칙만 적는다.

**보안 구현**

- 세션 쿠키는 `HttpOnly`·`Secure`·`SameSite=Lax` 를 **모든 프로파일에** 적용한다 (`application.yml`).
  주요 브라우저는 `http://localhost` 를 보안 컨텍스트로 보아 로컬에서도 `Secure` 쿠키를 받는다.
  받지 않는 브라우저(Safari 등)로 로컬 개발할 때만 `SESSION_COOKIE_SECURE=false` 로 끈다.
- TLS 를 프록시에서 끝내는 배포에서도 `Secure` 판정과 리다이렉트 주소가 맞도록 프록시 헤더를
  해석한다(`server.forward-headers-strategy: native`). Tomcat 은 사설망 주소의 프록시만 신뢰하므로
  외부에서 `X-Forwarded-*` 를 위조해도 반영되지 않는다.
- CSRF 토큰은 `XSRF-TOKEN` 쿠키(`HttpOnly`)에 두고, 클라이언트에는 `GET /api/auth/session`
  응답 본문으로 준다. 클라이언트는 `X-XSRF-TOKEN` 헤더로 돌려보내며, 헤더가 없으면 `403` 이다.
  본문의 토큰은 응답마다 다른 값으로 가린다(XOR 마스킹) — 압축된 응답 크기로 토큰을 추측하는
  공격(BREACH)의 단서를 없앤다.
- **로그인 시 세션 id 를 바꾸고, 로그인·로그아웃 시 CSRF 토큰 쿠키를 비운다** (공통 명세 §6.1).
  새 토큰은 다음 `GET /api/auth/session` 에서 발급된다. 임시 로그인과 가입은 컨트롤러가 직접
  로그인시키므로 Spring Security 의 세션 고정 방지·토큰 교체가 자동으로 돌지 않는다. OAuth 기존
  사용자의 로그인은 Spring Security 가 처리하지만 같은 결과가 되도록 맞춘다.
- CORS 허용 오리진은 프론트엔드 개발 서버(Vite, 기본 `http://localhost:5173`)이며 자격 증명
  포함 요청을 허용한다. **허용 오리진은 정확한 값의 목록으로만 준다.** 와일드카드 패턴이나
  요청 `Origin` 을 되돌려 주는 설정을 쓰면, 아무 사이트나 `GET /api/auth/session` 응답을 읽어
  CSRF 토큰을 가져갈 수 있다.
- **초대에는 추측 가능한 진입점이 없어야 한다.** 초대 id 로 접근한 요청은 초대를 찾기 전에
  관문을 거친다 — 수락·거절은 받은 본인인지 판정해 아니면 `404`, 철회는 그룹 소유자인지 판정해
  아니면 `403` 이다 (API 명세 §2.7). 순번 id 를 쓰더라도 이 판정이 유일한 관문이 된다.
- 파일 업로드 최대 요청 크기는 `spring.servlet.multipart.max-request-size` 로 제한한다.
- **서버 로그에 요청 본문·헤더·OAuth 쿼리를 남기지 않는다.** 초대 요청의 이메일(공통 명세 §3.1),
  세션 쿠키, 인가 코드가 응답에서 가려지는 것과 같은 이유로 로그에도 남지 않아야 한다. 요청
  로그는 메서드·경로·상태·소요 시간까지다. 기준 좌표(`lat`,`lng`)도 마찬가지로 남기지 않는다 —
  저장하지 않기로 한 값을 로그가 대신 보관하는 셈이 된다.

**로깅**

- 요청 한 건은 처리가 끝난 뒤 `INFO` 한 줄로 남긴다. 도메인 서비스 로그는 `DEBUG` 라 운영
  레벨에서는 걸러지고 요청 로그만 남는다. 한 줄에 담는 값은 위 보안 구현 항목이 정한 범위까지다.
- 출력 대상은 프로파일이 정한다 — `local`·`dev` 는 콘솔, `prod` 는 콘솔과 롤링 파일 양쪽이다.
  파일 경로는 `logging.file.name`(환경 변수 `LOG_FILE`)으로 덮을 수 있다.
- **레벨·파일 경로·보관 정책은 `application-{local,dev,prod}.yml` 이 소유하고,
  `logback-spring.xml` 은 프로파일별 appender 연결만 정한다.** 같은 값을 두 곳에 적으면 둘 중
  어느 쪽이 적용됐는지 읽어서는 알 수 없다.

**모니터링**

- 관리 엔드포인트(Spring Boot Actuator)는 **서비스 포트와 다른 관리 포트**(`management.server.port`,
  기본 8081)에서 연다. 수집기와 상태 확인은 로그인하지 않으므로 노출한 두 엔드포인트는 인증 없이 열려
  있고(§2.2.0), 같은 포트에 두면 외부에 그대로 노출된다. 배포에서 관리 포트를 공개하지 않는 것으로 막는다.
- 노출하는 엔드포인트는 상태 확인(`health`)과 지표 수집(`prometheus`) 둘뿐이다. `health` 는 구성 요소별
  상세(DB 상태 등) 없이 전체 상태만 응답한다.
- 지표는 Prometheus 가 관리 포트에서 읽어 가고 Grafana 로 본다. 모든 지표에 `application` 라벨을 붙인다.
- HTTP 요청 지표의 경로 라벨은 경로 템플릿(`/api/trips/{id}`)이다. 리소스 id 와 쿼리 문자열 — 기준
  좌표(`lat`,`lng`) 포함 — 은 지표에 남지 않는다.
- 관리 포트의 요청은 요청 로그에 남지 않는다. 요청 로그 필터는 서비스 포트에만 걸린다.
- **장소 검색의 외부 API 호출도 지표로 남긴다** — 호출 수·소요 시간·응답 상태다. 검색 한 번이 외부 호출
  여러 번으로 불어나므로(API 명세 §2.4.1) 실제 호출량을 따로 봐야 하고, 외부 장애는 서버 오류와 구분해 봐야 한다. 클라이언트에 관측 레지스트리(`ObservationRegistry`)를
  걸어 `http.client.requests` 지표를 받는다. 걸지 않으면 외부 호출이 지표에서 빠진다.
- **외부 호출 지표에 검색어가 남지 않아야 한다.** 검색어는 사용자가 입력한 값이라 요청 로그에도
  남기지 않는다. 경로 라벨은 URI 템플릿이며, 검색어를 쿼리 파라미터로 빌더에 붙이면 템플릿에
  들어가지 않는다.

**조회 정확성** — 이 항목들은 어기면 곧바로 정보 유출이거나 잘못된 건수다.

- **공개 범위 판정은 조회 쿼리 단계에서 수행한다.** 전체를 읽어 온 뒤 애플리케이션에서 걸러내면
  페이지네이션 건수가 어긋나고, 누락 시 곧바로 정보 유출이 된다.
- **기록 조회는 항상 소속 여행을 조인한다.** 판정 근거가 기록에 없으므로(§3.1) 조인 없는 기록
  조회 경로를 만들면 그 경로가 곧 우회로가 된다. 기록 목록·상세·태그 자동완성 모두 해당한다.
- 여행 목록의 `recordCount` 와 기록 목록의 건수는 **`deletedAt IS NULL` 인 기록만** 센다.
- 거리순 정렬(`sort=DISTANCE`)은 요청자의 기준 위치(`lat`,`lng`)로 서버에서 계산한다. 정렬은 DB(PostGIS)가
  `trip_records` 의 위치 인덱스로 하며, 응답의 `distanceKm` 는 애플리케이션이 같은 구면 거리로 계산한다.
  **이 좌표는 계산에만 쓰고 사용자와 묶어 저장하지 않는다** (공통 명세 §5).

**입력 검증**

- 모든 쓰기 API(`POST`/`PUT`/`PATCH`/`DELETE`)는 요청 바디 검증(Bean Validation)을 수행한다.
- 값 규칙 중 **평점 단위와 여행 기간 역전은 DB `CHECK` 제약으로도 막는다** (§3.1). 애플리케이션
  검증만 두면 우회 경로가 생겼을 때 잘못된 값이 그대로 들어간다.

## 6. 제약사항 및 향후 과제

도메인 차원의 미결 사항(공동 편집, 공개 링크, 방문일, 예산 범위 등)은
[공통 명세 §7](./specification.md) 에 있다. 여기서는 **백엔드가 해야 할 일**만 둔다.

### 6.1 구현 잔여 작업
- **OAuth 로그인과 가입 절차를 구현한다** (§2.1). 지금은 임시 인메모리 로그인만 동작한다.
  - `SecurityConfig` 의 주석 처리된 `oauth2Login` 블록을 되살리고, 성공 처리에 기존 사용자 로그인과
    가입 대기 분기를 둔다. OIDC 경로(`oidcUserService`)도 등록한다.
  - `UserService` 의 네이버 분기를 `OAuthProvider` 로 옮기고, 계정을 만들거나 이름·이메일을
    덮어쓰는 지금의 동작을 걷어낸다.
  - `POST /api/auth/signup`, `PATCH /api/auth/me` 를 추가한다.
  - 마이그레이션: `users.email` 컬럼과 `uk_users_email` 제약을 지우고, `lower(name)` 유니크 인덱스를
    더한다. 이미 있는 행의 `name` 이 겹치면 인덱스를 만들 수 없으므로 적용 전에 확인한다.
  - `local`·`dev` 전용 임시 로그인(`LocalLoginConfig`·`LocalLoginController`)은 남긴다.
  - 프론트엔드의 로그인·가입 화면(frontend §10.2).
- **`trips.cover_photo_id` 의 외래키를 복원할 수 있다** (§3). 마이그레이션 도구가 없어 생긴 제약이었고
  Flyway 를 도입해 풀렸다. 복원하면 사진 삭제 경로의 커버 해제를 `ON DELETE SET NULL` 이 대신한다.
  기록 삭제·소속 여행 변경 경로는 사진 행이 남으므로 애플리케이션의 해제가 계속 필요하다.
- **Flyway 도입 전에 만든 개발·dev DB 는 재생성해야 한다.** 마이그레이션 이력 테이블이 없어 V1 을
  적용할 수 없다. 운영 DB 는 확장(`postgis`, `pg_trgm`) 생성 권한을 갖춘 뒤 배포 절차에서 적용한다.
- **태그 API(`/api/tags`)는 구현되어 있으나 호출하는 화면이 없다.** 현재 등록 요청은 항상 빈
  태그 목록으로 들어온다 (frontend §10).
- **서비스 통계 배치를 구현한다** (§3.1 "서비스 통계"). 지금은 `loginDailyStatsJob` 이 로그인 일별 합계만
  남긴다. 남은 일은 셋이다 — 통계 테이블 다섯 개(`login_period_stats`, `user_daily_stats`,
  `trip_daily_stats`, `record_daily_stats`, `group_daily_stats`)를 추가하는 마이그레이션(V5),
  기존 잡을 흡수하고 `targetDate` 를 없앤 `dailyStatsJob`, `backend/README.md` 의 잡 실행 예시 갱신.

### 6.2 알려진 한계
- **막힌 사진이 브라우저 캐시에는 최대 1시간 남는다.** 사진 응답은 1시간 캐시되도록(`private`) 내려가므로,
  서버에서 바이너리를 지우거나 공개 범위를 좁혀도 이미 받아 둔 브라우저는 그동안 보여 준다
  (공통 명세 §7.2, API 명세 §2.5.3).
- **장소 검색 결과가 최대 5건이다** (API 명세 §2.4.1). 원 검색어 한 번만 호출하므로 원본 API의 검색어당
  5건 상한이 그대로 결과 상한이다. 검색어 변형 다중 호출로 후보를 늘리되, 그래도 부족하면 카카오
  로컬 API 등 다른 POI 데이터 소스 병행을 고려한다.
- **여행 단위 집계를 제공하지 않는다.** `recordCount` 외의 통계는 범위 밖이며, 평균 평점은
  의도적으로 집계하지 않는다 (§3.1).

### 6.3 외부 확인이 필요한 것
- 오브젝트 스토리지 전환 일정과 DB 에 저장된 사진의 이전 절차는 후속 명세에서 다룬다 (§4.2).
