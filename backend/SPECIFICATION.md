# 여행 지도 (travel-recorder) 백엔드 요구사항 정의서

## 1. 개요

### 1.1 이 문서의 범위
본 문서는 `backend/` 가 **무엇을 어떻게 구현하는지**를 정의한다 — 엔티티·테이블, 인증/인가 구현,
REST API 시그니처, 오류 코드, 사진 저장소 설계.

**서비스가 무엇인지는 여기서 정의하지 않는다.** 도메인 개념과 규칙, 공개 범위 정책, 외부 API의
구조적 제약, 모듈 간 계약은 [`../SPECIFICATION.md`](../SPECIFICATION.md) 가 소유하며
**충돌 시 공통 명세가 우선한다.** 본 문서는 그 규칙을 코드로 옮기는 방법만 적고, 규칙 자체를
다시 서술하지 않는다. 화면 요구사항은
[`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) 를 따른다.

용어는 [공통 명세 §3](../SPECIFICATION.md) 을 그대로 쓴다. 본 문서에서만 이름이 다른 것은
없으며, 엔티티 클래스명은 §3의 괄호 표기(`Trip`, `TripRecord`, `Group` …)와 일치시킨다.

### 1.2 실행 환경
- Kotlin 2.3 / Spring Boot 4.1 / Spring Data JPA / Spring Security OAuth2 Client
- 데이터베이스는 `local` 프로파일이 H2 인메모리(`MODE=PostgreSQL`), `dev`/`prod` 가 PostgreSQL 이다.
  세 프로파일 모두 `ddl-auto: validate` 라 엔티티와 `schema.sql` 이 어긋나면 기동 시점에 실패한다.
- OAuth2 클라이언트는 **네이버만 등록되어 있다** (§2.1).

> ⚠️ **여행 계층은 구현되어 있다.** 다만 **인가의 첫 관문이 아직 필터체인이 아니다** —
> `SecurityConfig` 가 열려 있어 컨트롤러가 `requireLogin` 으로 직접 막는다. 공개 범위 판정 자체는
> 서비스·조회 계층에 구현되어 있다. 남은 차이는 §8.1에 있다.

### 1.3 네이버 지역 검색 오픈API — 구현에 필요한 사실
제품 구분과 구조적 제약(검색어당 5건 상한, 거리순 정렬 미지원, CORS 미지원, 고유 장소 ID 부재)은
[공통 명세 §4](../SPECIFICATION.md) 에 있다. 여기서는 **백엔드가 코드로 처리해야 하는 것만** 적는다.

| 다뤄야 할 것 | 처리 |
|---|---|
| 응답 필드 | `title, category, address, roadAddress, telephone, mapx, mapy, link` |
| 좌표 변환 | `mapx`/`mapy` 는 정수형(10^7 배율) → **`/10,000,000` 변환 후** 저장·응답 (§4.5) |
| 호출 경로 | 브라우저가 직접 호출할 수 없으므로 **백엔드 프록시가 유일한 경로**다 (§4.5) |
| 키 보관 | 지역 검색 Client ID/Secret 은 서버 환경변수로만 두고 응답에 싣지 않는다 |
| 건수 부족 | 후보 풀이 요청 건수에 못 미쳐도 **오류가 아니다.** 확보된 만큼 반환한다 |

- `title` 에는 검색어 강조용 HTML 태그가 섞여 오므로 제거 후 저장한다.
- 좌표계와 일일 호출 한도는 구현 착수 시점에 공식 문서로 재검증한다 (§8.3).

## 2. 인증 및 인가

### 2.1 로그인

> **현재 구현은 임시 인메모리 로그인이다.** 네이버 OAuth 는 실제 키와 콜백 도메인이 갖춰지기
> 전까지 비활성 상태이며, 아래 네이버 관련 내용은 복구 대상 명세로 남겨 둔다 (§8.1).
>
> - 로그인: `POST /api/auth/login` — JSON `{ "username", "password" }`. 성공 시 세션 쿠키를
>   발급하고 `MeResponse` 를 반환하며, 실패는 `401 UNAUTHENTICATED` 다 (§6 의 공통 오류 형식).
> - 계정은 `local`·`dev` 프로파일에서만 등록되는 고정 3개(`user1`~`user3`, 비밀번호
>   `password`)다. 고정 비밀번호 계정을 운영 환경에 열지 않기 위해 프로파일을 제한한다.
> - 이 계정들은 기동 시 `users` 에 `provider = "local"`, `provider_id = username` 으로
>   upsert 된다. 초대는 이메일로 상대를 찾으므로(§4.8), 상대가 한 번도 로그인하지 않아도
>   행이 있어야 한다.
> - 로그인 수단이 둘이 되면서 principal 구현 타입도 둘이다. 컨트롤러는 수단이 아니라
>   `LoginUser`(사용자 PK 만 노출하는 인터페이스)에 의존한다.

- 로그인 수단은 **네이버 OAuth 2.0 하나만** 지원한다.
- Spring Security OAuth2 Client 의 Authorization Code 플로우를 사용한다.
  - 로그인 시작: `GET /oauth2/authorization/naver`
  - 콜백: `GET /login/oauth2/code/naver` (Spring Security 기본 처리)
- 네이버는 사용자 프로필을 최상위 `response` 객체로 감싸 반환하므로, `UserService`
  (`OAuth2UserService` 구현체)가 이를 평탄화한다. 사용자 식별자는 `response.id` 다.
- 네이버 애플리케이션 설정에서 이메일 주소·이름(별명)·프로필 사진을 "필수"로 활성화해야 한다.
- 로그인 성공 시 세션 쿠키(`JSESSIONID`)를 발급한다. 프론트엔드는 이후 요청에
  `credentials: 'include'` 로 쿠키를 포함한다.
- 최초 로그인 계정은 `User` 로 자동 가입(upsert)하며, 이후 `provider("naver") + providerId` 로 매칭한다.
- 로그아웃: `POST /api/auth/logout` — 세션을 무효화한다.

### 2.2 인가 정책

권한 규칙 자체는 [공통 명세 §2.6](../SPECIFICATION.md) 에 있다. 여기서는 **그 규칙을 코드에서
어떻게 판정하고 어떤 상태 코드로 응답하는지**를 정의한다.

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
- **기록 등록도 `canEdit(trip)` 를 통과해야 한다.** 볼 수 없는 여행 ID로 등록을 시도하면
  `404 TRIP_NOT_FOUND` 다 (아래 은닉 규칙).

#### 2.2.2 실패 시 상태 코드

| 상황 | 상태 | code |
|---|---|---|
| 비로그인으로 인증 필요 엔드포인트 접근 | 401 | `UNAUTHENTICATED` |
| `canView` 실패 — 여행 | 404 | `TRIP_NOT_FOUND` |
| `canView` 실패 — 기록 (소속 여행을 못 보는 경우 포함) | 404 | `RECORD_NOT_FOUND` |
| `canView` 는 통과하나 `canEdit` 실패 | 403 | `FORBIDDEN` |
| 당사자가 아닌 초대에 접근 (보낸 소유자·받은 사람이 아님) | 404 | `INVITE_NOT_FOUND` |

- **열람 권한이 없으면 `403` 이 아니라 `404` 다.** 응답 본문이 실제로 없는 것과 구분되지 않아야
  하며, 메시지 문구도 같아야 한다 (§6).
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
- 같은 은닉 원칙이 **하위 리소스 ID**에도 적용된다. 남의 여행 사진 ID를 커버로 지정하려 하면
  `404 PHOTO_NOT_FOUND`, 속하지 않은 그룹 ID로 공유하려 하면 `404 GROUP_NOT_FOUND` 다.

## 3. 도메인 모델

아래는 엔티티의 **구조**(필드와 관계)만 보여 준다. 길이·범위·필수 여부 같은 **값 규칙은 §3.1이
유일한 출처**이며 주석에 중복해 적지 않는다.

```
User
  id: Long (PK)
  provider: String              // "naver" (고정)
  providerId: String            // 네이버 회원번호 (response.id)
  email: String
  name: String
  profileImageUrl: String?
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
  visibility: Visibility        // PRIVATE | GROUP | PUBLIC
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

Photo
  id: Long (PK)
  record: TripRecord (FK)
  storageKey: String            // 저장소 내 식별 경로/키
  originalFileName: String
  contentType: String
  fileSizeBytes: Long
  createdAt: Instant
  // uploader 컬럼은 두지 않는다 — 사진을 올릴 수 있는 사람이 작성자뿐이라 record.trip.owner 와 항상 같다

Group                           // 조회 전용 공유 대상 목록 (테이블명 share_group, §3.1)
  id: Long (PK)
  owner: User (FK)
  name: String                  // 그룹 이름
  memo: String?                 // 그룹 설명. 소유자와 멤버 모두에게 보인다
  createdAt: Instant

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

TripShare                       // trip.visibility=GROUP 일 때만 사용
  id: Long (PK)
  trip: Trip (FK)
  group: Group (FK)
  // unique(trip_id, group_id)
```

`Trip.coverPhoto` 와 `Photo.record` 는 `Trip → TripRecord → Photo → Trip` 순환 참조를 만든다.
`cover_photo_id` 컬럼은 `trips` 에 두되 **외래키는 선언하지 않는다.** 순환 때문에 인라인 FK 로는
어떤 테이블 순서로도 선언할 수 없고, `ALTER TABLE ADD CONSTRAINT` 는 PostgreSQL 에 `IF NOT EXISTS`
가 없어 `schema.sql` 을 재실행하는 기동에서 실패하기 때문이다 (§8.1).

따라서 **사진이 지워질 때 커버 지정을 푸는 것은 애플리케이션의 책임**이다. 사진이 사라져도 여행은
남아야 하므로 이 처리를 빠뜨리면 여행 조회가 깨진다. 마이그레이션 도구를 도입하면 FK 와
`ON DELETE SET NULL` 로 되돌린다.

### 3.1 제약 조건

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
  **요청자가 소유한 여행**이어야 하며, 아니면 `404 TRIP_NOT_FOUND` 다. 옮기는 순간 그 기록의
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

- `Group` 은 SQL 예약어라 테이블명을 `share_group` 으로 둔다 (엔티티 클래스명은 `Group`).
- `Group.memo` 는 선택이며 길이 상한은 공통 명세 §3.6이 정한다. 공백만 들어오면 `null` 로 저장해
  "메모 없음" 과 같은 값으로 만든다. **요청자에 따라 달라지지 않는다** —
  그룹을 조회할 수 있는 사람은 소유자와 멤버뿐이고(§4.7) 둘 다 메모를 볼 수 있으므로, DTO 에
  요청자별 분기를 두지 않는다.
- **그룹당 멤버는 소유자 포함 최대 5명**이다. 애플리케이션 레벨에서 검증하며, 초과 시
  `409 GROUP_MEMBER_LIMIT_EXCEEDED` 다. 그룹 생성 시 소유자를 `GroupMember` 로 함께 입력한다.
- `GroupInvite` 는 `unique(group_id, invitee_id)` 다. 대기 중인 초대가 있는 상대를 다시
  초대해도 행이 늘지 않고 기존 초대가 그대로 유지된다 (멱등, §4.8).
- **초대 대상은 가입자만이다.** 요청의 이메일과 일치하는 `User` 가 없으면 `404 USER_NOT_FOUND`
  이며, 이메일 비교는 대소문자를 구분하지 않는다. 이미 `GroupMember` 인 사용자를 초대하면
  `409 ALREADY_MEMBER` 다.
- **정원 판정은 수락 시점에만 한다.** 대기 중인 `GroupInvite` 는 정원에 포함되지 않으므로,
  정원이 찬 뒤의 수락은 `409 GROUP_MEMBER_LIMIT_EXCEEDED` 로 거부된다. 이때 **초대 행은 지우지
  않는다** — 자리가 나면 같은 초대로 다시 수락할 수 있어야 한다 (공통 명세 §3.7).
- `TripShare` 는 `Trip.visibility` 가 `GROUP` 일 때만 의미가 있다. `PRIVATE`/`PUBLIC` 으로
  바꿀 때 기존 공유 행은 삭제한다 — 남겨 두면 나중에 `GROUP` 으로 되돌렸을 때 의도치 않은
  공유가 되살아난다.
- 소유자는 **본인이 소유하거나 멤버로 속한 그룹에만** 여행을 공유할 수 있다. 그 외 그룹 ID가
  요청에 담기면 `404 GROUP_NOT_FOUND` 다 (존재 은닉).
- **기록 단위의 공유 관계는 존재하지 않는다.** 이전 판의 `VisitRecordShare` 는 폐기되었다.
- **`InviteHistory` 는 초대 행을 지우는 모든 경로에서, 지우는 것과 같은 트랜잭션 안에 남긴다.**
  경로는 넷이다 — 수락·거절·취소(철회)·그룹 삭제. 한 곳만 빠뜨려도 이력이 조용히 비고,
  그런 누락은 조회 시점에 드러나지 않는다.
- **`InviteHistory.groupId` 에는 외래키를 걸지 않는다.** 그룹 삭제 시 이력까지 함께 지워지거나
  삭제가 막히면 이력을 남기는 의미가 없기 때문이다. 대신 `groupName` 스냅샷을 함께 저장해,
  그룹이 사라진 뒤에도 무엇에 대한 초대였는지 답할 수 있게 한다 (공통 명세 §3.7).
- **정원 초과로 수락이 거부된 경우는 이력을 남기지 않는다.** 초대 행이 그대로 남아 있으므로
  끝난 것이 아니다 (§4.8).
- `Tag` 는 이름 중복 없이 재사용되며, 존재하지 않는 태그명이 등록 요청에 포함되면 서버가 생성한다.

### 3.2 삭제 정책 (soft delete)
`Trip` 과 `TripRecord` 는 물리 삭제하지 않고 `deletedAt` 에 삭제 시각을 기록한다. 통계·이력
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
  대표하게 두면 그 불변식이 깨진다. 사진 행과 파일 자체는 남는다.
- `Photo` 는 파일 본체를 함께 지워야 하므로 soft delete 대상이 아니며 물리 삭제한다.
  삭제된 사진을 커버로 쓰던 여행은 `cover_photo_id` 가 `NULL` 이 된다. DB 제약이 아니라
  애플리케이션이 지우는 시점에 해제한다 (§3).
- **`Group`, `GroupMember`, `GroupInvite`, `TripShare` 는 물리 삭제한다.** 공유 해제와
  그룹 탈퇴는 즉시 조회 권한을 없애야 하는 동작이라, 남아 있는 행이 권한 판정에 끼어들 여지를
  만들지 않는다.
- **초대는 수락·거절·철회·그룹 삭제 어느 쪽으로 끝나든 `group_invite` 행을 지우고, 같은 트랜잭션에서
  `InviteHistory` 를 남긴다** (공통 명세 §3.7). 상태 컬럼을 달아 같은 행에 두지 않는 이유는
  `unique(group_id, invitee_id)` 때문이다 — 끝난 초대가 그 자리에 남아 있으면 같은 상대를
  다시 초대할 수 없다. 부분 유니크 인덱스로 우회할 수는 있으나 H2(local)와 PostgreSQL 의
  문법이 갈리고 마이그레이션 도구가 없다 (§8.1).
- **`InviteHistory` 는 지우지 않는다.** soft delete 대상도 아니다. 보관 기간을 두지 않으며,
  사용자 탈퇴 시의 처리는 탈퇴 자체와 함께 범위 밖이다 (공통 명세 §3.1).
- 그룹 삭제 시 그 그룹의 `GroupMember`, `GroupInvite`, `TripShare` 행을 함께 지운다.
  이때 **대기 중이던 초대는 `GROUP_DELETED` 로 이력에 남긴다** — 받은 사람 쪽에서 보면
  초대가 이유 없이 사라지는 일이라 그 이유를 남겨 둘 자리가 필요하다.
  그 그룹으로만 공유되던 여행은 실질적으로 비공개가 되며, 여행과 기록 자체는 삭제되지 않는다.
- `updatedAt` 은 마지막 수정 시각이며 soft delete 도 수정으로 보아 함께 갱신된다.

## 4. API 명세

기본 경로: `/api`. 응답은 JSON. 목록 조회는 페이지네이션을 지원하며, 요청이 받는 것은
`page`(0-base) 하나다 (§4.1).

### 4.1 목록 조회의 공통 규칙

**조회 범위 (`scope`)** — **여행·기록 목록**은 **어떤 범위를 보는지**를 항상 명시한다. 세 범위는
성격이 달라 한 목록에 섞지 않으며, `scope` 는 그 두 목록의 **필수 파라미터**다. 초대 목록(§4.8)은
당사자 것만 보이므로 고를 범위가 없어 받지 않는다.

| `scope` | 여행 목록에서 | 기록 목록에서 | 비로그인 |
|---|---|---|:---:|
| `mine` | `owner` 가 요청자인 여행 전부 (공개 범위 무관) | 그 여행들의 하위 기록 | `401` |
| `shared` | 요청자가 멤버인 그룹으로 공유된 **타인의** 여행 | 그 여행들의 하위 기록 | `401` |
| `public` | `visibility=PUBLIC` 인 여행 전부 (본인 것 포함) | 그 여행들의 하위 기록 | ✅ |

- `scope` 는 **권한을 넓히는 수단이 아니다.** 어떤 값이든 서버는 §2.2 판정을 통과한 것만
  반환한다. 이미 볼 수 있는 것 중에서 고르는 수단일 뿐이다.
- `scope=shared` 는 본인 것을 제외한다. 본인 것은 `mine` 에서 이미 전부 보이므로 중복을 피한다.
- **기록 목록의 범위 판정도 소속 여행으로 한다** (§2.2). 기록 자체에는 판정 근거가 없다.

**페이지네이션** — 모든 목록 응답은 `content`, `page`(0-base), `size`, `totalElements`,
`totalPages` 를 포함한다. **요청이 받는 것은 `page` 하나뿐이고 페이지 크기는 서버가 정한다.**
크기를 클라이언트가 고르게 두면 한 번에 전부 받아 가는 요청을 막을 상한이 매번 필요해지고,
그 상한 자체가 또 하나의 값 규칙이 된다.

| 목록 | 페이지 크기 |
|---|---|
| 여행 목록 (`/api/trips`) | 10 |
| 기록 목록 (`/api/records`) | 10 |
| 장소 검색 (`/api/places/search`) | 5 |
| 초대 목록 (`/api/invites`, `/api/groups/{id}/invites`) | 10 |

- **요청에 `size` 를 담아도 무시한다.** 응답의 `size` 는 서버가 적용한 크기를 알려주는 값이다.
- `page` 가 음수면 `0` 으로 보고, 범위를 넘는 `page` 는 오류가 아니라 **빈 `content`** 다.
- **장소 검색만 5건인 것은 원본 API가 호출당 5건까지만 주기 때문이다** (공통 명세 §4.2 1항).
  페이지 크기를 그보다 키우면 첫 페이지조차 채우지 못한 채 다음 페이지가 늘 비게 된다 (§4.5).
- 여행 상세의 하위 기록도 같은 스키마를 쓴다 (`tripId` 로 한정, §4.4.1).

### 4.2 인증

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/api/auth/login` | **임시** 인메모리 로그인. `local`·`dev` 전용 (§2.1) | - |
| GET | `/oauth2/authorization/naver` | 네이버 로그인 시작 (리다이렉트). **현재 비활성** (§2.1) | - |
| GET | `/api/auth/me` | 현재 로그인 사용자 정보 조회. 비로그인 시 `401` | 선택 |
| POST | `/api/auth/logout` | 로그아웃, 세션 무효화 | 필요 |

### 4.3 여행

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/trips` | 여행 목록 조회 (§4.3.1) | 선택 |
| GET | `/api/trips/{id}` | 여행 상세. 열람 권한 없으면 `404` | 선택 |
| POST | `/api/trips` | 여행 생성 | 필요 |
| PUT | `/api/trips/{id}` | 여행 기본 정보 수정 (공개 범위 제외) | 소유자만 |
| PATCH | `/api/trips/{id}/visibility` | 공개 범위·공유 그룹만 변경 | 소유자만 |
| PATCH | `/api/trips/{id}/cover` | 커버 사진 지정·해제 | 소유자만 |
| DELETE | `/api/trips/{id}` | 여행 삭제 (하위 기록 함께 soft delete) | 소유자만 |

#### 4.3.1 여행 목록 조회

```
GET /api/trips?scope=mine&keyword=제주&sort=recent&page=0
```

| 파라미터 | 값 | 설명 |
|---|---|---|
| `scope` | `mine` \| `shared` \| `public` | **필수.** 조회 범위 (§4.1) |
| `keyword` | 문자열 | 여행 이름 부분 일치 |
| `sort` | `recent`(기본) \| `startDate` | `recent` 는 생성 시각 역순, `startDate` 는 시작일 역순 |
| `page` | 정수 | 0-base, 기본 `0`. 페이지 크기는 10 고정 (§4.1) |

- `sort=distance` 는 여행 목록에 없다. 여행은 좌표를 갖지 않기 때문이다.
- `visibility`·`sharedGroups` 는 **요청자가 소유자인 여행에만** 응답에 포함한다.
  `budget` 은 열람 권한이 있으면 누구에게나 내려준다 (공통 명세 §3.5 노출 표).

**응답 예시 (`scope=mine`)**
```json
{
  "content": [
    {
      "id": 12,
      "name": "제주 3박 4일",
      "startDate": "2026-09-05",
      "endDate": "2026-09-08",
      "headcount": 4,
      "budget": 1250000,
      "coverPhotoUrl": "/api/files/photos/2026/09/14/abc123.jpg",
      "recordCount": 11,
      "owner": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
      "isOwner": true,
      "visibility": "GROUP",
      "sharedGroups": [{ "id": 2, "name": "가족" }],
      "createdAt": "2026-09-09T10:00:00Z"
    }
  ],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
}
```

- `recordCount` 는 삭제되지 않은 하위 기록 수다. 0 일 수 있다 (§3.1).
- `coverPhotoUrl` 은 커버가 지정되지 않았으면 `null` 이다. **서버는 대체 이미지를 고르지 않는다** —
  무엇을 대신 보여줄지는 화면의 판단이다.

**POST `/api/trips` 요청 예시**
```json
{
  "name": "제주 3박 4일",
  "startDate": "2026-09-05",
  "endDate": "2026-09-08",
  "headcount": 4,
  "budget": 1250000,
  "memo": "가족들과 다녀온 첫 제주",
  "visibility": "GROUP",
  "groupIds": [2]
}
```

- `visibility` 생략 시 `PRIVATE` 이다.
- `groupIds` 는 `visibility=GROUP` 일 때만 의미가 있으며, 그 외 값일 때 함께 오면 무시한다.
- `groupIds` 가 빈 배열인 `GROUP` 여행은 허용한다. 소유자 외에는 아무도 볼 수 없는 상태이며,
  이를 프론트엔드가 안내한다 (공통 명세 §3.5).
- `budget`·`memo` 는 생략 가능하다. `coverPhotoId` 는 생성 요청에 담을 수 없다 — 지정할 사진이
  아직 존재할 수 없기 때문이다 (§4.3.2).

**GET `/api/trips/{id}` 응답 예시 (요청자가 소유자인 경우)**
```json
{
  "id": 12,
  "name": "제주 3박 4일",
  "startDate": "2026-09-05",
  "endDate": "2026-09-08",
  "headcount": 4,
  "budget": 1250000,
  "memo": "가족들과 다녀온 첫 제주",
  "coverPhotoUrl": "/api/files/photos/2026/09/14/abc123.jpg",
  "recordCount": 11,
  "owner": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
  "isOwner": true,
  "visibility": "GROUP",
  "sharedGroups": [{ "id": 2, "name": "가족" }],
  "createdAt": "2026-09-09T10:00:00Z",
  "updatedAt": "2026-09-09T10:00:00Z"
}
```

- 요청자가 소유자가 아니면 `isOwner: false` 이며 **`visibility` 와 `sharedGroups` 를 응답에서
  제외한다.** "누구에게 공유했는지"는 열람자에게 알릴 이유가 없기 때문이다. **`budget` 은
  제외하지 않는다** — 예산은 공개 범위를 그대로 따르는 값이다 (공통 명세 §3.5 노출 표).
- **하위 기록 목록은 이 응답에 포함되지 않는다.** `GET /api/records?tripId={id}` 로 따로 조회한다
  (§4.4.1). 여행당 기록 수에 제한이 없어 상세 응답이 무한정 커지는 것을 막기 위해서다.

**PATCH `/api/trips/{id}/visibility` 요청 예시**
```json
{ "visibility": "PRIVATE" }
```
```json
{ "visibility": "GROUP", "groupIds": [2, 5] }
```
- 공유 그룹 목록은 **전체 교체**다. 빠진 그룹의 공유는 해제된다.
- `PRIVATE`/`PUBLIC` 으로 바꾸면 기존 `TripShare` 행을 모두 삭제한다 (§3.1).
- 좁히는 변경은 즉시 적용되어, 직전까지 열람 가능하던 사용자는 이후 **여행과 하위 기록 전부에
  대해** `404` 를 받는다.

#### 4.3.2 커버 사진 지정

```json
PATCH /api/trips/{id}/cover
{ "photoId": 11 }
```
```json
{ "photoId": null }
```

- `photoId` 는 **그 여행의 하위 기록에 속한 사진**이어야 한다. 다른 여행의 사진이거나 존재하지
  않으면 `404 PHOTO_NOT_FOUND` 다 (존재 은닉).
- `null` 을 보내면 커버 지정이 해제된다.
- 커버 지정은 사진 업로드 이후에만 가능하다 (공통 명세 §6.2).

### 4.4 여행 기록

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/records` | 목록 조회 (§4.4.1) | 선택 |
| GET | `/api/records/{id}` | 상세 조회. 열람 권한 없으면 `404` | 선택 |
| POST | `/api/records` | 기록 등록 (소속 여행 지정 필수) | 여행 소유자만 |
| PUT | `/api/records/{id}` | 기록 수정 (전체 갱신) | 소유자만 |
| PATCH | `/api/records/{id}/trip` | 소속 여행 변경 | 소유자만 |
| DELETE | `/api/records/{id}` | 기록 삭제 (soft delete) | 소유자만 |

- **`PATCH /api/records/{id}/visibility` 는 폐기되었다.** 공개 범위는 기록에 없으므로
  `PATCH /api/trips/{id}/visibility` 로 대체된다 (§4.3).

#### 4.4.1 목록 조회

```
GET /api/records?scope=mine&tripId=12&category=FOOD&tag=제주&keyword=카페
                &sort=recent&lat=37.55&lng=126.97&page=0
```

| 파라미터 | 값 | 설명 |
|---|---|---|
| `scope` | `mine` \| `shared` \| `public` | **필수.** 조회 범위 (§4.1) |
| `tripId` | 정수 | 특정 여행의 하위 기록으로 한정. 여행 상세 화면이 이 경우다 |
| `category` | `SIGHT`\|`SHOPPING`\|`FOOD` | 생략 시 전체 |
| `tag`, `keyword` | 문자열 | 태그 일치, 장소명·주소 부분 일치 |
| `sort` | `recent`(기본) \| `rating` \| `distance` | `rating` 은 기록의 평점 기준 |
| `lat`, `lng` | 실수 | `sort=distance` 일 때 필수. 요청자의 기준 위치 |
| `page` | 정수 | 0-base, 기본 `0`. 페이지 크기는 10 고정 (§4.1) |

- **범위 판정은 모두 소속 여행을 조인해서 한다** (§2.2, §4.1). 기록 테이블만 봐서는 판정할 수 없다.
- `tripId` 로 지정한 여행을 볼 수 없으면 `404 TRIP_NOT_FOUND` 다. `scope` 와 `tripId` 가
  어긋나면(예: `scope=mine` 인데 타인의 공개 여행) 오류가 아니라 **빈 목록**을 반환한다.
- **응답에 `visibility` 와 공유 그룹 목록은 포함하지 않는다.** 그 값은 여행에 있으므로
  `GET /api/trips/{id}` 로 조회한다.

**응답 예시 (`scope=public`)**
```json
{
  "content": [
    {
      "id": 3,
      "name": "성산일출봉",
      "category": "SIGHT",
      "address": "제주특별자치도 서귀포시 성산읍 성산리 1",
      "latitude": 33.458031,
      "longitude": 126.942520,
      "rating": 4.5,
      "memo": "일출 시간에 맞춰 올라갔는데 최고였습니다",
      "thumbnailUrl": "/api/files/photos/2026/09/14/abc123.jpg",
      "photoCount": 3,
      "trip": { "id": 12, "name": "제주 3박 4일" },
      "author": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
      "distanceKm": 1.2,
      "createdAt": "2026-09-08T09:12:00Z"
    }
  ],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
}
```

- `trip` 은 소속 여행의 식별자와 이름만 담는다. 여행의 기간·인원·예산까지 매 기록마다 반복해서
  내려주지 않는다 — 필요하면 `GET /api/trips/{id}` 로 조회한다.
- `author` 는 `trip.owner` 를 그대로 옮긴 값이다 (§3.1).

**POST `/api/records` 요청 예시**
```json
{
  "tripId": 12,
  "name": "성산일출봉",
  "category": "SIGHT",
  "tags": ["일출명소", "가족여행"],
  "address": "제주특별자치도 서귀포시 성산읍 성산리 1",
  "roadAddress": "제주특별자치도 서귀포시 성산읍 일출로 284-12",
  "externalLink": "https://map.naver.com/...",
  "latitude": 33.458031,
  "longitude": 126.942520,
  "rating": 4.5,
  "memo": "일출 시간에 맞춰 올라갔는데 최고였습니다"
}
```

- **`tripId` 는 필수다.** 생략하면 `400 VALIDATION_ERROR`, 요청자가 소유하지 않은 여행이면
  `404 TRIP_NOT_FOUND` 다 (존재 은닉, §2.2).
- **`visibility` 와 `groupIds` 는 이 요청에 없다.** 공개 범위는 여행이 이미 갖고 있다 (§4.3).

**GET `/api/records/{id}` 응답 예시 (요청자가 소유자인 경우)**
```json
{
  "id": 3,
  "trip": { "id": 12, "name": "제주 3박 4일" },
  "name": "성산일출봉",
  "category": "SIGHT",
  "tags": ["일출명소", "가족여행"],
  "address": "제주특별자치도 서귀포시 성산읍 성산리 1",
  "roadAddress": "제주특별자치도 서귀포시 성산읍 일출로 284-12",
  "externalLink": "https://map.naver.com/...",
  "latitude": 33.458031,
  "longitude": 126.942520,
  "rating": 4.5,
  "memo": "일출 시간에 맞춰 올라갔는데 최고였습니다",
  "photos": [
    { "id": 11, "url": "/api/files/photos/2026/09/14/abc123.jpg" }
  ],
  "author": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
  "isAuthor": true,
  "createdAt": "2026-09-08T09:12:00Z",
  "updatedAt": "2026-09-08T09:12:00Z"
}
```

- `isAuthor` 는 요청자가 `trip.owner` 인지를 뜻한다. 필드 이름은 이전 판 그대로 유지한다.
- **`visibility` 와 `sharedGroups` 는 기록 응답에 포함되지 않는다** (소유자에게도 마찬가지다).
  여행의 값이므로 `GET /api/trips/{id}` 에서 내려간다.

**PATCH `/api/records/{id}/trip` 요청 예시**
```json
{ "tripId": 15 }
```
- 기록을 다른 여행으로 옮긴다. 대상 여행은 **요청자가 소유한 여행**이어야 하며, 아니면
  `404 TRIP_NOT_FOUND` 다.
- 옮기는 즉시 그 기록의 공개 범위는 새 여행의 것이 된다. **범위를 넓히는 이동일 수 있으므로**
  프론트엔드가 결과를 확인시킨다 (공통 명세 §3.5).
- 기록에 달린 사진이 이전 여행의 커버로 지정되어 있었다면 그 커버 지정은 해제된다 —
  커버는 자기 여행의 사진만 가리킬 수 있기 때문이다 (§3.1).

### 4.5 장소 검색 (등록용, 네이버 지역 검색 집계)

기록 등록 폼에서 장소를 고를 때 사용하는 검색이다. 백엔드가 네이버 지역 검색 오픈API를 서버
사이드에서 호출·집계해 제공한다 (§1.3). API 키를 프론트엔드에 노출하지 않기 위해 반드시 백엔드를
경유한다. **이 API는 저장 단위가 아니라 외부 조회 결과를 돌려줄 뿐이며, 경로가 `/api/records` 와
분리된 이유도 그것이다.**

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/places/search?keyword={keyword}&lat={lat}&lng={lng}&page={page}` | 지역 검색 결과를 집계·정렬·페이지네이션하여 반환 | 선택 |

**동작 방식**
1. 원 검색어 및 보조 변형(지역명 결합 등)으로 **여러 번 호출**해 후보를 모으고 `(name, address)`
   기준으로 중복을 제거한다.
2. 각 후보의 `mapx`/`mapy` 를 WGS84로 변환한다 (§1.3).
3. `lat`/`lng` 가 주어지면 거리를 계산해 가까운 순으로 정렬하고, 없으면 원본 API 순서를 유지한다.
4. 집계된 전체 후보를 **5건 단위**로 페이지네이션한다. 원본 API가 호출당 5건까지만 주므로
   페이지 크기를 그보다 키워도 채울 수 없다 (§4.1).
5. 원본 API 자체가 검색어당 5건으로 제한되므로 확보된 만큼만 반환하며, `totalElements` 로
   실제 확보 건수를 그대로 알려준다.

**응답 예시**
```json
{
  "content": [
    {
      "name": "성산일출봉",
      "category": "관광,명소 > 자연명소",
      "address": "제주특별자치도 서귀포시 성산읍 성산리 1",
      "roadAddress": "제주특별자치도 서귀포시 성산읍 일출로 284-12",
      "telephone": "064-783-0959",
      "latitude": 33.458031,
      "longitude": 126.942520,
      "distanceKm": 1.2,
      "link": "https://map.naver.com/..."
    }
  ],
  "page": 0, "size": 5, "totalElements": 4, "totalPages": 1
}
```

- 응답의 `category` 는 네이버가 내려주는 분류 문자열이며 서비스 카테고리(`SIGHT` 등)와 별개다.
- `distanceKm` 은 `lat`/`lng` 가 주어졌을 때만 포함된다.
- 사용자가 후보를 선택하면 프론트엔드가 해당 값을 그대로 `POST /api/records` 요청에 채워 넣는다.

### 4.6 사진

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/api/records/{id}/photos` | 사진 업로드 (multipart/form-data, 필드명 `files`, 다중 첨부 가능) | 소유자만 |
| DELETE | `/api/records/{id}/photos/{photoId}` | 사진 삭제 | 소유자만 |
| GET | `/api/files/photos/**` | 저장된 사진 파일 서빙(정적 리소스) | 선택 |

- 업로드 제약(크기·포맷)은 **공통 명세 §6.3이 유일한 출처**이며 양쪽 모듈이 같은 값을 쓴다.
  백엔드는 이를 설정값으로 외부화한다.
  서버는 Content-Type/확장자를 검증하고 위반 시 `400 INVALID_FILE` 을 반환한다.
- **사진을 올리고 지울 수 있는 사람은 소속 여행의 소유자뿐이다.**
- 삭제된 사진이 어느 여행의 커버였다면 그 여행의 `cover_photo_id` 는 `NULL` 이 된다. 외래키가 없어
  사진 삭제 처리가 직접 해제해야 한다 (§3).
  커버 지정 자체는 `PATCH /api/trips/{id}/cover` 로 한다 (§4.3.2).
- ⚠️ `GET /api/files/photos/**` 는 **공개 범위를 적용하지 않는다.** 경로를 아는 사람은 비공개
  여행의 사진도 볼 수 있으며, 현재는 추측 불가능한 UUID 경로에만 의존한다 (§8).

### 4.7 그룹

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/groups` | 내가 소유하거나 속한 그룹 목록 (`role: OWNER\|MEMBER` 포함) | 필요 |
| POST | `/api/groups` | 그룹 생성 (소유자를 멤버로 함께 입력) | 필요 |
| GET | `/api/groups/{id}` | 그룹 상세 (멤버 목록 포함) | 소유자·멤버 |
| PUT | `/api/groups/{id}` | 그룹 이름·메모 변경 | 소유자만 |
| DELETE | `/api/groups/{id}` | 그룹 삭제 (멤버·대기 초대·공유 관계 함께 삭제) | 소유자만 |
| DELETE | `/api/groups/{id}/members/{userId}` | 멤버 제외 | 소유자만 |
| DELETE | `/api/groups/{id}/members/me` | 그룹 탈퇴. 소유자와 비멤버는 `403` | 멤버 본인 |

**GET `/api/groups/{id}` 응답 예시**
```json
{
  "id": 2,
  "name": "가족",
  "memo": "설 연휴 사진 공유용",
  "owner": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
  "members": [
    { "id": 7, "name": "홍길동", "profileImageUrl": "https://...", "joinedAt": "2026-09-01T00:00:00Z" },
    { "id": 9, "name": "김영희", "profileImageUrl": "https://...", "joinedAt": "2026-09-03T11:20:00Z" }
  ],
  "memberCount": 2,
  "memberLimit": 5,
  "isOwner": true
}
```
- 멤버 정보는 이름과 프로필 사진까지만 내려준다. **이메일은 포함하지 않는다** (공통 명세 §3.1).
- 멤버가 아닌 사용자의 상세 조회·탈퇴는 `403 FORBIDDEN` 이다. 존재하지 않는 그룹만 `404` 다 (§2.2.2).
- 소유자 제외 요청(`DELETE .../members/{소유자 id}`)은 `400 VALIDATION_ERROR` 다.
- POST·PUT 요청 본문은 `{ "name": "가족", "memo": "설 연휴 사진 공유용" }` 이다. `memo` 는 생략
  가능하며 생략하면 `null` 로 저장된다. PUT 은 두 값을 함께 덮어쓰므로 **`memo` 를 빼고 보내면
  기존 메모가 지워진다** — 이름만 고치는 경우에도 현재 메모를 함께 실어 보내야 한다.
- 목록(`GET /api/groups`)의 각 항목도 `memo` 를 포함한다. 그룹 카드에서 이름만으로 구분되지 않는
  문제를 풀려고 넣은 값이라, 상세로 들어가야만 보이면 목적을 채우지 못한다.

### 4.8 초대

초대는 **보내는 쪽(그룹)** 과 **받는 쪽(사용자)** 두 경로로 나뉜다. 공개 토큰 URL은 존재하지
않으며 모든 엔드포인트가 로그인을 요구한다 (공통 명세 §2.1).

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/groups/{id}/invites` | 그 그룹의 **대기 중인 초대 목록** | 소유자만 |
| POST | `/api/groups/{id}/invites` | 이메일로 초대 보내기 | 소유자만 |
| DELETE | `/api/groups/{id}/invites/{inviteId}` | 초대 철회 | 소유자만 |
| GET | `/api/invites` | **내가 받은 대기 중인 초대 목록** | 필요 |
| GET | `/api/invites/sent` | **내가 보낸 대기 중인 초대 목록** (그룹을 가로질러) | 필요 |
| GET | `/api/invites/history?role=received\|sent` | **끝난 초대 이력** | 당사자만 |
| POST | `/api/invites/{inviteId}/accept` | 수락 → 멤버로 가입 | 받은 본인만 |
| POST | `/api/invites/{inviteId}/reject` | 거절 | 받은 본인만 |

- **두 목록 모두 페이지네이션한다** (§4.1). **건수를 묶어 주는 상한이 없기 때문이다** — 대기 초대는
  정원 판정이 수락 시점이라 정원을 넘겨 보낼 수 있고, 받은 초대는 나를 초대할 수 있는 그룹 수에
  제한이 없다. 정원(5명)이 곧 상한인 그룹 목록(§4.7)과는 다르다.
- 수락·거절·철회는 해당 행을 지우고 같은 트랜잭션에서 이력을 남긴다 (§3.1, §3.2).
  성공 응답은 본문 없이 `204 No Content` 다.
- **`GET /api/invites/sent` 의 조건은 `invited_by = 나` 다.** 그룹을 조인해 소유자를 보는 것과
  결과가 같지만(소유자는 바뀌지 않는다) 조인 없이 인덱스 하나로 끝나고, 남이 보낸 초대가
  섞일 수 없어 인가가 조건 자체로 보장된다. `group_invite(invited_by)` 인덱스를 추가한다 —
  현재 인덱스는 `invitee_id` 와 `uk_group_invite`(선두 `group_id`) 뿐이라 이 조회가 풀스캔이 된다.
- **`GET /api/invites/history` 는 `role` 로 관점을 고른다.** `received` 면 `invitee_id = 나`,
  `sent` 면 `invited_by = 나` 이며, 생략하면 `received` 다. 정렬은 `resolvedAt DESC` 이고
  `invite_history(invitee_id, resolved_at DESC)`·`(invited_by, resolved_at DESC)` 인덱스를 둔다.
- **`GET /api/invites/sent` 의 각 항목은 `{ id, group: { id, name }, invitee, createdAt }` 다.**
  그룹을 가로지르는 목록이라 그룹명이 함께 필요하며, 그룹 상세에서 쓰는 응답(`invitee`·`createdAt`
  만 있는 형태)과 나누어 둔다 — 그쪽은 어느 그룹인지가 화면에 이미 드러나 있다.
- **이력도 페이지네이션한다** (§4.1). 대기 초대와 달리 상한이 없을 뿐 아니라 지우지 않으므로
  계정이 오래될수록 길어진다.

**POST `/api/groups/{id}/invites` 요청·응답 예시**
```json
{ "email": "friend@example.com" }
```
```json
{
  "id": 31,
  "invitee": { "id": 9, "name": "김영희", "profileImageUrl": "https://..." },
  "createdAt": "2026-09-20T09:12:00Z"
}
```
- 이메일은 **완전 일치**로만 사용자를 찾으며 대소문자는 구분하지 않는다. 부분 일치 검색이나
  사용자 목록 조회 엔드포인트는 만들지 않는다 (공통 명세 §3.7).
- **응답에 이메일을 담지 않는다.** 소유자가 직접 입력한 값이라도 되돌려주지 않으며, 상대는
  이름·프로필 사진으로 식별한다 (공통 명세 §3.1, §3.7).
- 대기 중인 초대가 이미 있는 상대를 다시 초대하면 **새 행을 만들지 않고 기존 초대를 `200` 으로
  반환한다.** 중복 클릭이 실패처럼 보이지 않게 하기 위해서이며, `unique(group_id, invitee_id)`
  가 이를 보장한다 (§3.1). 새로 만들어진 경우만 `201` 이다.
- 실패 응답: 가입자가 없으면 `404 USER_NOT_FOUND`, 이미 멤버면 `409 ALREADY_MEMBER` 다.
  **가입 여부를 은닉하지 않는 것은 의도된 선택이다** — 소유자가 오타를 알아차릴 유일한 수단이며,
  그 대가는 공통 명세 §7.2에 한계로 적혀 있다.

**GET `/api/invites` 응답 예시**
```json
{
  "content": [
    {
      "id": 31,
      "group": { "id": 2, "name": "가족" },
      "invitedBy": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
      "createdAt": "2026-09-20T09:12:00Z"
    }
  ],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
}
```
- **수락 전에는 그룹명·초대자·보낸 시각까지만 내려준다.** 멤버 목록도, 그 그룹으로 공유된 여행도
  포함하지 않는다 (공통 명세 §3.7).
- **당사자가 아닌 초대는 `404 INVITE_NOT_FOUND` 다.** 보낸 소유자와 받은 사람 외에는 그 초대의
  존재가 드러나지 않아야 하며, 없는 `inviteId` 와 응답이 같아야 한다 (§2.2.2).
- 수락 시 정원(5명)이 차 있으면 `409 GROUP_MEMBER_LIMIT_EXCEEDED` 이고 **초대 행은 남는다.**
  자리가 난 뒤 같은 초대로 다시 수락할 수 있어야 하기 때문이다 (§3.1).
- 수락 처리는 정원 검사와 `GroupMember` 입력을 **한 트랜잭션에서, 그룹 행을 잠근 뒤** 수행한다.
  정원을 강제하는 DB 제약이 없어 애플리케이션 검사가 유일한 관문이므로, 검사와 입력 사이에 다른
  수락이 끼어들면 6명짜리 그룹이 만들어진다.
- 그룹이 삭제되면 그 그룹의 초대도 함께 사라지므로(§3.2), 이미 받은 목록에 있던 초대의 수락이
  `404 INVITE_NOT_FOUND` 가 될 수 있다. 정상 동작이다.

**GET `/api/invites/history?role=sent` 응답 예시**
```json
{
  "content": [
    {
      "id": 12,
      "group": { "id": 2, "name": "가족", "deleted": false },
      "counterpart": { "id": 9, "name": "김영희", "profileImageUrl": "https://..." },
      "outcome": "REJECTED",
      "invitedAt": "2026-09-20T09:12:00Z",
      "resolvedAt": "2026-09-21T02:40:00Z"
    }
  ],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
}
```
- **상대는 `role` 에 따라 달라지므로 `counterpart` 한 필드로 내려준다.** `role=sent` 면 초대받았던
  사람, `role=received` 면 보냈던 사람이다. 관점마다 필드 이름을 달리하면 화면이 같은 목록을
  두 가지 모양으로 다뤄야 한다.
- `group.name` 은 **끝난 시점의 스냅샷**이다. 그룹 이름이 그 뒤에 바뀌어도 이력은 따라 바뀌지
  않으며, 그룹이 삭제되었으면 `deleted: true` 로 내려 화면이 링크를 걸지 않게 한다
  (공통 명세 §3.7).
- `outcome` 은 `ACCEPTED` / `REJECTED` / `REVOKED` / `GROUP_DELETED` 넷이다. **만료는 없다** —
  초대에 수명이 없기 때문이다.
- **이력도 당사자만 본다.** `role` 과 무관하게 남의 이력은 조회 경로가 없으며, 조건이 곧
  본인 필터다 (공통 명세 §2.6).

### 4.9 태그

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/tags` | 태그 목록/자동완성 (쿼리 `keyword`) | 선택 |

- 자동완성 후보는 **요청자가 볼 수 있는 기록에 쓰인 태그**로 제한한다. 전체 태그를 그대로
  내려주면 비공개 기록에만 쓰인 태그가 노출되기 때문이다.
- 볼 수 있는지는 §2.2 대로 **소속 여행을 조인해서** 판정한다. 태그 조회라고 해서 판정을
  건너뛰면 그대로 정보 유출이 된다.

## 5. 사진 저장소 설계

### 5.1 현재 단계 — 서버 파일시스템
- 업로드된 파일은 설정된 루트 경로(예: `app.storage.local.root-dir`) 아래에
  `yyyy/MM/dd/{UUID}.{ext}` 형태로 저장한다.
- `Photo.storageKey` 에는 저장소 루트 기준 상대 경로를 저장한다 (예: `2026/09/14/abc123.jpg`).
- 파일은 `GET /api/files/photos/**` 정적 리소스 핸들러를 통해 서빙한다.

### 5.2 향후 전환 — 오브젝트 스토리지(AWS S3)
- `PhotoStorageService` 인터페이스로 저장 로직을 추상화하여 구현체를
  `FileSystemPhotoStorageService`(현재) / `S3PhotoStorageService`(향후) 로 교체 가능하게 한다.

```kotlin
interface PhotoStorageService {
    fun store(file: MultipartFile, record: TripRecord): StoredPhoto
    fun delete(storageKey: String)
    fun resolveUrl(storageKey: String): String
}
```

- `application.yml` 의 `app.storage.type=filesystem|s3` 설정값으로 구현체를 선택한다.
- API 응답의 `photos[].url` 은 저장 방식과 무관하게 항상 접근 가능한 URL을 반환하므로,
  프론트엔드는 저장소 전환과 무관하게 동작한다.
- S3 전환 시 **서명 URL(pre-signed URL)** 을 도입하면 §4.6의 공개 범위 미적용 문제를 함께
  해결할 수 있다. 전환 시점에 함께 검토한다 (§8).

## 6. 오류 응답 형식

공통 오류 응답 포맷 (`@ControllerAdvice` 기반):

```json
{ "code": "RECORD_NOT_FOUND", "message": "존재하지 않는 기록입니다.", "status": 404 }
```

**모든 실패 응답은 이 형태로 통일한다.** 컨트롤러에 도달하기 전에 실패하는 경우(주소 없음,
메서드 불일치, 본문 파싱 실패 등)와 예상하지 못한 예외까지 `GlobalExceptionHandler` 가 받아
같은 스키마로 변환하므로, 프론트엔드는 상태 코드와 무관하게 `code` 로 분기할 수 있다.

| 상황 | HTTP 상태 | code |
|---|---|---|
| 입력값 검증 실패 (필수값 누락, 카테고리 값 오류, 평점 범위·단위 오류, 여행 기간 역전, 인원·예산 범위 오류) | 400 | `VALIDATION_ERROR` |
| 경로·쿼리 파라미터 타입 불일치, 필수 파라미터(`scope` 등) 누락, 본문 파싱 실패 | 400 | `VALIDATION_ERROR` |
| 허용되지 않는 파일 형식/용량 초과 (멀티파트 단계 초과 포함) | 400 | `INVALID_FILE` |
| 비로그인 사용자의 보호된 API 접근 | 401 | `UNAUTHENTICATED` |
| 권한 없는 수정/삭제 시도 (열람은 가능한 경우), 멤버가 아닌 그룹의 조회·탈퇴 | 403 | `FORBIDDEN` |
| 존재하지 않거나 **열람 권한이 없는** 여행 | 404 | `TRIP_NOT_FOUND` |
| 존재하지 않거나 **열람 권한이 없는** 기록 | 404 | `RECORD_NOT_FOUND` |
| 존재하지 않는 그룹, 여행 공유 요청에 담긴 접근 불가 그룹 id | 404 | `GROUP_NOT_FOUND` |
| 존재하지 않는 사진 | 404 | `PHOTO_NOT_FOUND` |
| 존재하지 않거나 **당사자가 아닌** 초대 | 404 | `INVITE_NOT_FOUND` |
| 초대 대상 이메일의 가입자가 없음 | 404 | `USER_NOT_FOUND` |
| 매핑되지 않은 주소, 존재하지 않는 정적 파일 | 404 | `NOT_FOUND` |
| 지원하지 않는 HTTP 메서드 | 405 | `METHOD_NOT_ALLOWED` |
| 지원하지 않는 `Content-Type` | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 그룹 정원(5명) 초과 | 409 | `GROUP_MEMBER_LIMIT_EXCEEDED` |
| 이미 멤버인 사용자를 초대 | 409 | `ALREADY_MEMBER` |
| 그 밖의 DB 제약 위반 (동시 요청 경합 등) | 409 | `CONFLICT` |
| 네이버 지역 검색 오픈API 호출 실패/한도 초과 | 502 | `PLACE_SEARCH_UNAVAILABLE` |
| 그 밖의 처리되지 않은 예외 | 500 | `INTERNAL_ERROR` |

- `500` 과 `409` 의 `message` 는 고정 문구로 내려간다. 예외 메시지에 테이블·제약 이름이나 내부
  구조가 드러날 수 있어 응답에 싣지 않고 서버 로그로만 남긴다.
- `400` 의 `message` 는 어느 필드가 왜 틀렸는지 담는다 (예: `rating: 평점은 0.5점 단위로만 입력할 수 있습니다.`).
- **`404 RECORD_NOT_FOUND`·`TRIP_NOT_FOUND` 의 `message` 는 존재하지 않는 경우와 권한 없는 경우가
  동일해야 한다.** 문구가 다르면 그 차이만으로 존재가 드러난다.
- **소속 여행을 볼 수 없어 가려지는 기록은 `RECORD_NOT_FOUND` 로 응답한다** (§2.2).
  `TRIP_NOT_FOUND` 를 쓰면 기록의 존재가 드러난다.

## 7. 비기능 요구사항

세션 쿠키·CSRF·CORS의 **원칙**은 [공통 명세 §6.1](../SPECIFICATION.md) 에 있다. 여기서는
백엔드가 지켜야 할 구현 수단과, 공통 명세에 없는 서버 측 규칙만 적는다.

**보안 구현**

- 세션 쿠키는 `HttpOnly`, 운영 환경에서는 `Secure` 속성을 적용한다.
- CSRF 토큰은 `XSRF-TOKEN` 쿠키(JS 접근 가능)로 내려주고 `X-XSRF-TOKEN` 헤더로 받는다.
  헤더가 없으면 `403` 이다.
- CORS 허용 오리진은 프론트엔드 개발 서버(Vite, 기본 `http://localhost:5173`)이며 자격 증명
  포함 요청을 허용한다.
- **초대에는 추측 가능한 진입점이 없어야 한다.** 초대 id 로 접근한 요청은 당사자인지 먼저
  판정하고, 아니면 `404` 다 (§4.8). 순번 id 를 쓰더라도 이 판정이 유일한 관문이 된다.
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

**조회 정확성** — 이 항목들은 어기면 곧바로 정보 유출이거나 잘못된 건수다.

- **공개 범위 판정은 조회 쿼리 단계에서 수행한다.** 전체를 읽어 온 뒤 애플리케이션에서 걸러내면
  페이지네이션 건수가 어긋나고, 누락 시 곧바로 정보 유출이 된다.
- **기록 조회는 항상 소속 여행을 조인한다.** 판정 근거가 기록에 없으므로(§3.1) 조인 없는 기록
  조회 경로를 만들면 그 경로가 곧 우회로가 된다. 기록 목록·상세·태그 자동완성 모두 해당한다.
- 여행 목록의 `recordCount` 와 기록 목록의 건수는 **`deletedAt IS NULL` 인 기록만** 센다.
- 거리순 정렬(`sort=distance`)은 요청자의 기준 위치(`lat`,`lng`)로 서버에서 계산한다.
  **이 좌표는 계산에만 쓰고 사용자와 묶어 저장하지 않는다** (공통 명세 §5).

**입력 검증**

- 모든 쓰기 API(`POST`/`PUT`/`PATCH`/`DELETE`)는 요청 바디 검증(Bean Validation)을 수행한다.
- 값 규칙 중 **평점 단위와 여행 기간 역전은 DB `CHECK` 제약으로도 막는다** (§3.1). 애플리케이션
  검증만 두면 우회 경로가 생겼을 때 잘못된 값이 그대로 들어간다.

## 8. 제약사항 및 향후 과제

도메인 차원의 미결 사항(공동 편집, 공개 링크, 방문일, 예산 범위 등)은
[공통 명세 §7](../SPECIFICATION.md) 에 있다. 여기서는 **백엔드가 해야 할 일**만 둔다.

### 8.1 구현 잔여 작업
- **초대 이력이 명세만 개정된 상태다** (§3, §4.8). `invite_history` 테이블과 세 조회
  엔드포인트(`/api/invites/sent`, `/api/invites/history`)가 아직 없고, 초대를 지우는 네 경로도
  이력을 남기지 않는다. 기존에 끝난 초대는 복원할 수 없으므로 **이력은 전환 시점부터 쌓인다.**
- **네이버 OAuth 로그인을 복구한다** (§2.1). 임시 인메모리 로그인으로 대체된 상태이며,
  되돌릴 지점은 셋이다 — `SecurityConfig` 의 주석 처리된 `oauth2Login` 블록, `local`·`dev`
  전용인 `LocalLoginConfig`·`LocalLoginController`(들어내면 된다), 그리고 프론트엔드의 로그인
  화면(frontend §10.2). `UserService` 와 `application.yml` 의 네이버 등록 정보는 손대지 않았다.
- **`SecurityConfig` 의 `permitAll()` 을 §2.2 정책으로 되돌린다.** 공개 범위 판정은 서비스·조회
  계층에 구현되어 있으나, 인증 자체는 아직 컨트롤러가 `requireLogin` 으로 막는다. 인가의 첫
  관문을 필터체인으로 되돌리는 작업이 남아 있다.
- **스키마 마이그레이션 도구(Flyway/Liquibase)가 없다.** `schema.sql` 이
  `CREATE TABLE IF NOT EXISTS` 기반이라 이미 만들어진 테이블에 컬럼을 추가하지 못한다. 여행 계층
  전환은 이 스크립트를 새로 써서 반영했으므로 **기존 개발·dev DB 는 재생성해야 한다.**
  이 제약의 대가를 두 곳에서 치르고 있다.
    - `trips.cover_photo_id` 에 외래키가 없다. 순환 참조라 인라인 선언이 불가능하고
      `ALTER TABLE ADD CONSTRAINT` 는 재실행되지 않기 때문이다 (§3). 도구를 도입하면
      FK 와 `ON DELETE SET NULL` 로 되돌리고, 커버 해제를 애플리케이션에서 뺄 수 있다.
    - 구조가 바뀔 때마다 DB 재생성이 필요하다.

  **운영 데이터가 생기기 전에** 도입해야 한다.
- **태그 API(`/api/tags`)는 구현되어 있으나 호출하는 화면이 없다.** 현재 등록 요청은 항상 빈
  태그 목록으로 들어온다 (frontend §10).

### 8.2 알려진 한계
- **사진 서빙에 공개 범위가 적용되지 않는다** (§4.6). 추측 불가능한 UUID 경로에만 의존하는
  상태이며, 서명 URL 또는 인가 기반 서빙으로의 전환이 후속 과제다 (§5.2).
- **장소 검색 후보 풀이 부족할 수 있다** (§4.5). 원본 API의 검색어당 5건 상한 때문에 다중 호출
  집계로도 요청 건수를 못 채울 수 있다. 구현 착수 전 재검토하고, 필요 시 카카오 로컬 API 등
  다른 POI 데이터 소스 병행을 고려한다.
- **여행 단위 집계를 제공하지 않는다.** `recordCount` 외의 통계는 범위 밖이며, 평균 평점은
  의도적으로 집계하지 않는다 (§3.1).

### 8.3 외부 확인이 필요한 것
- 네이버 지역 검색 오픈API의 **정확한 `mapx`/`mapy` 좌표계**와 **일일 호출 한도**는 구현 착수
  시점에 developers.naver.com 공식 문서로 재검증해야 한다 (§1.3).
- 오브젝트 스토리지 전환 일정과 기존 파일시스템 데이터 마이그레이션 절차는 후속 명세에서 다룬다 (§5.2).
