# 여행 지도 (travel-recorder) 백엔드 요구사항 정의서

## 1. 개요

### 1.1 목적
사용자가 방문한 장소를 **개인 기록**으로 남기고 원하는 상대에게만 선택적으로 공유하는 서비스의
백엔드 API를 정의한다. 사용자는 네이버 계정으로 로그인하여 방문 기록을 등록하고, 사진·메모·평점을
남기며, 각 기록의 공개 범위를 직접 정한다. 장소 입력은 백엔드가 프록시하는 네이버 지역 검색
결과를 이용한다.

### 1.2 범위
본 문서는 `backend/` (Kotlin + Spring Boot) 의 도메인 모델, 인증/인가 정책, REST API,
사진 저장소 설계를 정의한다. 서비스 전반의 공통 규칙은 [`../SPECIFICATION.md`](../SPECIFICATION.md)
가 정의하며 **충돌 시 공통 명세가 우선한다.** 화면 요구사항은
[`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) 를 따른다.

### 1.3 용어
[공통 명세 §1.3](../SPECIFICATION.md) 을 따른다. 본 문서에서 자주 쓰는 것만 옮기면:
방문 기록(VisitRecord, 개인 소유 저장 단위), 공개 범위(`PRIVATE`/`GROUP`/`PUBLIC`),
그룹(조회 전용 공유 대상 목록), 평점(기록당 1개, 작성자 본인의 평가).

### 1.4 현재 구현 기준과 괴리
- Kotlin 2.3 / Spring Boot 4.1 / Spring Data JPA / Spring Security OAuth2 Client
- 데이터베이스는 `local` 프로파일이 H2 인메모리(`MODE=PostgreSQL`), `dev`/`prod` 가 PostgreSQL 이다.
  세 프로파일 모두 `ddl-auto: validate` 라 엔티티와 `schema.sql` 이 어긋나면 기동 시점에 실패한다.
- OAuth2 클라이언트는 **네이버만 등록되어 있다** (§2.1).
- `SecurityConfig` 는 개발 편의를 위해 `anyRequest().permitAll()` 로 임시 비활성화되어 있고,
  인증이 필요한 엔드포인트는 컨트롤러가 `requireLogin(principal)` 로 직접 막고 있다.
- 방문 기록·공개 범위·공유 그룹·초대는 본 명세대로 구현되어 있다. 이전 모델의 `Place` 공유
  엔티티와 `Review` 는 제거되었다. 남은 차이는 §8에 정리한다.

### 1.5 네이버 API 사용 범위 확인
본 서비스가 쓰는 "네이버 지도 API"는 발급 경로와 제공 기능이 다른 두 제품으로 나뉜다.

| 제품 | 발급처 | 제공 기능 | 본 서비스에서의 용도 |
|---|---|---|---|
| NAVER Cloud Platform (NCP) Maps | ncloud.com (별도 콘솔 가입, 과금형) | Geocoding, Reverse Geocoding, Static/Dynamic Map(JS SDK), Directions | **지도 표시(임베드)** 는 프론트엔드가 NCP Client ID로 `naver.maps.Map` SDK를 직접 렌더링한다 (백엔드 개입 없음). 좌표↔주소 변환이 필요하면 백엔드에서 Geocoder를 보조적으로 사용할 수 있다 |
| 네이버 검색 오픈API – 지역(Local) 검색 | developers.naver.com (무료 애플리케이션 등록) | 키워드로 장소/업체 검색 → `title, category, address, roadAddress, telephone, mapx, mapy, link` | **§4.3 장소 검색 API 의 실제 구현체** (백엔드가 집계·가공 후 제공) |

- **키워드 기반 장소 검색은 NCP Maps 단독으로 제공되지 않는다.** 지역 검색 오픈API를 반드시 함께
  사용해야 하며, 두 제품의 인증키(Client ID/Secret)는 서로 다르다.
- 지역 검색 오픈API는 **응답에 안정적인 장소 고유 ID가 없다.** 따라서 방문 기록에는 외부 장소 ID
  대신 검색 결과의 장소명·주소·좌표·원본 링크를 **스냅샷으로 복사해** 저장한다 (§3).
- 지역 검색 오픈API는 **한 번의 호출당 최대 5건**까지만 반환하며(`display` 최대 5, `start` 사실상 1),
  **좌표/반경 기반 검색이나 거리순 정렬 파라미터를 지원하지 않고**, 브라우저에서 직접 호출할 수
  없다(CORS 미지원) — 반드시 백엔드에서 프록시해야 한다. 일일 호출 한도는 구현 시점에 재확인한다.
- 응답 좌표(`mapx`, `mapy`)는 WGS84 경위도가 아닌 **정수형(10^7 배율) 좌표**로 내려오므로
  `/10,000,000` 변환 후 저장·응답한다. 실제 값이 다를 가능성에 대비해 구현 착수 시 표본 응답으로
  한 번 더 검증한다.
- 프론트엔드는 장소 검색 결과를 **10건씩 페이지네이션, 기준 위치 거리순 정렬**로 요구한다.
  원본 API가 검색어당 5건으로 제한되므로 **백엔드가 여러 검색어 변형으로 후보를 모아 집계**해야만
  충족 가능하며, 그럼에도 확보 가능한 후보 풀 자체가 제한적일 수 있다 (§8).

## 2. 인증 및 인가

### 2.1 로그인
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

인가 판정의 기준은 **요청자가 그 기록을 볼 수 있는가**이며, 다음 세 가지 중 하나라도 참이면 열람 가능하다.

1. 요청자가 기록의 `author` 다
2. 기록의 `visibility` 가 `PUBLIC` 이다
3. 기록의 `visibility` 가 `GROUP` 이고, 요청자가 그 기록이 공유된 그룹 중 하나의 멤버다

| 행위 | 비로그인 | 로그인 사용자 | 열람 가능자 | 작성자 본인 |
|---|:---:|:---:|:---:|:---:|
| 기록 조회 (목록/상세) | `PUBLIC` 만 | 위 판정에 따름 | ✅ | ✅ |
| 기록 등록 | ❌ | ✅ | - | - |
| 기록 수정·삭제 | ❌ | ❌ | ❌ | ✅ |
| 공개 범위·공유 그룹 변경 | ❌ | ❌ | ❌ | ✅ |
| 사진 업로드·삭제 | ❌ | ❌ | ❌ | ✅ |
| 장소 검색 | ✅ | ✅ | ✅ | ✅ |
| 그룹 생성 | ❌ | ✅ | - | - |
| 그룹 조회 | ❌ | ❌ | ✅ 멤버 | ✅ 소유자 |
| 초대 링크 발급·멤버 제외·그룹 삭제 | ❌ | ❌ | ❌ | ✅ 그룹 소유자 |
| 초대 수락 | ❌ | ✅ | - | - |
| 그룹 탈퇴 | ❌ | ❌ | ✅ 본인 | ❌ 소유자는 불가 |

- **편집 권한은 `VisitRecord.author` 로만 판단하며 그룹과 무관하다.** 멤버라는 사실은 조회 권한만 준다.
- 인증이 필요한 엔드포인트에 비로그인으로 접근하면 `401 UNAUTHENTICATED` 다.
- **열람 권한이 없는 기록 조회는 `403` 이 아니라 `404 RECORD_NOT_FOUND` 다.** 기록의 존재 자체를
  숨기기 위해서이며, 응답 본문이 실제로 없는 기록과 구분되지 않아야 한다.
- 열람은 가능하지만 수정·삭제 권한이 없는 경우(= 작성자가 아닌 사용자의 수정 시도)는 `403 FORBIDDEN` 이다.
  이 경우 기록의 존재는 이미 열람으로 드러나 있으므로 숨길 이유가 없다.

## 3. 도메인 모델

```
User
  id: Long (PK)
  provider: String              // "naver" (고정)
  providerId: String            // 네이버 회원번호 (response.id)
  email: String
  name: String
  profileImageUrl: String?
  createdAt: Instant

VisitRecord                     // 개인 소유 방문 기록 — 서비스의 유일한 저장 단위
  id: Long (PK)
  author: User (FK)             // 작성자. 편집 권한의 유일한 근거
  name: String                  // 장소명 (외부 검색 결과 스냅샷)
  category: Category            // SIGHT | SHOPPING | FOOD
  address: String               // 지번 주소 (스냅샷)
  roadAddress: String?          // 도로명 주소 (스냅샷)
  externalLink: String?         // 지역 검색 결과 원본 링크. 안정적 장소 ID가 없어 참고용
  latitude: Double              // WGS84
  longitude: Double             // WGS84
  rating: Double                // 0.5~5.0, 0.5 단위, 필수. 작성자 본인의 평가
  memo: String?                 // 자유 텍스트 설명, 1000자 이하, 선택
  visibility: Visibility        // PRIVATE | GROUP | PUBLIC, 기본 PRIVATE
  createdAt: Instant
  updatedAt: Instant
  deletedAt: Instant?           // soft delete (§3.2)

Tag
  id: Long (PK)
  name: String (unique)

VisitRecordTag                  // VisitRecord - Tag 다대다 조인
  record: VisitRecord (FK)
  tag: Tag (FK)

Photo
  id: Long (PK)
  record: VisitRecord (FK)
  storageKey: String            // 저장소 내 식별 경로/키
  originalFileName: String
  contentType: String
  fileSizeBytes: Long
  createdAt: Instant
  // uploader 컬럼은 두지 않는다 — 사진을 올릴 수 있는 사람이 작성자뿐이라 record.author 와 항상 같다

Group                           // 조회 전용 공유 대상 목록 (테이블명 share_group, §3.1)
  id: Long (PK)
  owner: User (FK)
  name: String                  // 30자 이하
  createdAt: Instant

GroupMember
  id: Long (PK)
  group: Group (FK)
  user: User (FK)
  joinedAt: Instant
  // unique(group_id, user_id)

GroupInvite
  id: Long (PK)
  group: Group (FK)             // unique — 그룹당 유효 초대 1건
  token: String (unique)        // 추측 불가능한 난수 (URL-safe, 최소 128비트)
  expiresAt: Instant            // 발급 + 7일
  createdAt: Instant

VisitRecordShare                // visibility=GROUP 일 때만 사용
  id: Long (PK)
  record: VisitRecord (FK)
  group: Group (FK)
  // unique(record_id, group_id)
```

### 3.1 제약 조건
- `VisitRecord.category` 는 `SIGHT`(관광지) / `SHOPPING`(쇼핑) / `FOOD`(맛집) 중 하나만 허용한다.
- `VisitRecord.visibility` 는 `PRIVATE` / `GROUP` / `PUBLIC` 중 하나이며 **기본값은 `PRIVATE`** 이다.
  요청에서 생략되면 `PRIVATE` 로 저장한다.
- `VisitRecord.latitude`, `longitude` 는 필수이며 WGS84 위경도(소수점 6자리 이상)로 저장한다.
  지역 검색 결과의 `mapx`/`mapy` 는 저장 전 변환한다 (§1.5).
- `VisitRecord.rating` 은 **0.5 ~ 5.0 범위의 0.5 단위 값**만 허용하며 **필수**다. `Double` 로
  저장하는데 0.5 배수는 이진 부동소수점으로 정확히 표현되므로 비교에 오차가 생기지 않는다.
  요청 DTO 의 Bean Validation 과 DB `CHECK` 제약 양쪽에서 검증한다.
- **평균 평점을 집계하지 않는다.** 기록마다 평점이 하나뿐이라 집계 대상이 없다. 이전 판의
  `rating.average` / `rating.count` 응답 필드는 폐기되었다.
- **같은 장소의 기록이 여러 건 존재하는 것이 정상이다.** 중복 판정도, 중복 방지 제약도 두지 않는다.
- `Group` 은 SQL 예약어라 테이블명을 `share_group` 으로 둔다 (엔티티 클래스명은 `Group`).
- **그룹당 멤버는 소유자 포함 최대 5명**이다. 애플리케이션 레벨에서 검증하며, 초과 시
  `409 GROUP_MEMBER_LIMIT_EXCEEDED` 다. 그룹 생성 시 소유자를 `GroupMember` 로 함께 입력한다.
- `GroupInvite` 는 그룹당 1건만 존재한다 (`group_id` 유니크). 재발급은 기존 행의 `token` 과
  `expiresAt` 을 갱신하는 방식이라 이전 토큰은 즉시 무효가 된다.
- `VisitRecordShare` 는 `visibility` 가 `GROUP` 일 때만 의미가 있다. `PRIVATE`/`PUBLIC` 으로
  바꿀 때 기존 공유 행은 삭제한다 — 남겨 두면 나중에 `GROUP` 으로 되돌렸을 때 의도치 않은
  공유가 되살아난다.
- 작성자는 **본인이 소유하거나 멤버로 속한 그룹에만** 기록을 공유할 수 있다. 그 외 그룹 ID가
  요청에 담기면 `404 GROUP_NOT_FOUND` 다 (존재 은닉).
- `Tag` 는 이름 중복 없이 재사용되며, 존재하지 않는 태그명이 등록 요청에 포함되면 서버가 생성한다.

### 3.2 삭제 정책 (soft delete)
`VisitRecord` 는 물리 삭제하지 않고 `deletedAt` 에 삭제 시각을 기록한다. 통계·이력 보존과
오삭제 복구를 위해서다.

- **살아 있는 행의 기준은 `deletedAt IS NULL` 하나뿐이다.** `isDeleted` / `deleteYn` 같은 별도
  플래그는 두지 않는다. 같은 사실을 두 컬럼에 적으면 둘이 어긋나는 순간 어느 쪽이 맞는지 알 수
  없고, `deletedAt` 만으로 "삭제 여부"와 "삭제 시각"을 모두 답할 수 있다.
- 조회 제외는 엔티티의 `@SQLRestriction("deleted_at is null")` 이 처리한다. 삭제된 기록은 `404` 다.
- `Photo` 는 파일 본체를 함께 지워야 하므로 soft delete 대상이 아니며 물리 삭제한다.
- **`Group`, `GroupMember`, `GroupInvite`, `VisitRecordShare` 는 물리 삭제한다.** 공유 해제와
  그룹 탈퇴는 즉시 조회 권한을 없애야 하는 동작이라, 남아 있는 행이 권한 판정에 끼어들 여지를
  만들지 않는다.
- 그룹 삭제 시 그 그룹의 `GroupMember`, `GroupInvite`, `VisitRecordShare` 행을 함께 지운다.
  그 그룹으로만 공유되던 기록은 실질적으로 비공개가 되며, 기록 자체는 삭제되지 않는다.
- `updatedAt` 은 마지막 수정 시각이며 soft delete 도 수정으로 보아 함께 갱신된다.

## 4. API 명세

기본 경로: `/api`. 응답은 JSON. 목록 조회는 페이지네이션을 지원한다 (`page`(0-base), `size`).

### 4.1 인증

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/oauth2/authorization/naver` | 네이버 로그인 시작 (리다이렉트) | - |
| GET | `/api/auth/me` | 현재 로그인 사용자 정보 조회. 비로그인 시 `401` | 선택 |
| POST | `/api/auth/logout` | 로그아웃, 세션 무효화 | 필요 |

### 4.2 방문 기록

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/records` | 목록 조회 (§4.2.1) | 선택 |
| GET | `/api/records/{id}` | 상세 조회. 열람 권한 없으면 `404` | 선택 |
| POST | `/api/records` | 기록 등록 | 필요 |
| PUT | `/api/records/{id}` | 기록 수정 (공개 범위 포함 전체 갱신) | 작성자만 |
| PATCH | `/api/records/{id}/visibility` | 공개 범위·공유 그룹만 변경 | 작성자만 |
| DELETE | `/api/records/{id}` | 기록 삭제 (soft delete) | 작성자만 |

#### 4.2.1 목록 조회

```
GET /api/records?scope=mine&category=FOOD&tag=제주&keyword=카페
                &sort=recent&lat=37.55&lng=126.97&page=0&size=10
```

| 파라미터 | 값 | 설명 |
|---|---|---|
| `scope` | `mine` \| `shared` \| `public` | **필수.** 조회 범위 (공통 명세 §6.2) |
| `category` | `SIGHT`\|`SHOPPING`\|`FOOD` | 생략 시 전체 |
| `tag`, `keyword` | 문자열 | 태그 일치, 장소명·주소 부분 일치 |
| `sort` | `recent`(기본) \| `rating` \| `distance` | `rating` 은 기록의 평점 기준 |
| `lat`, `lng` | 실수 | `sort=distance` 일 때 필수. 요청자의 기준 위치 |
| `page`, `size` | 정수 | 기본 `0`, `10` |

| `scope` | 반환 대상 | 비로그인 |
|---|---|:---:|
| `mine` | `author` 가 요청자인 기록 전부 (공개 범위 무관) | `401` |
| `shared` | 요청자가 멤버인 그룹으로 공유된 **타인의** 기록 | `401` |
| `public` | `visibility=PUBLIC` 인 기록 전부 (본인 것 포함) | ✅ |

- `scope` 는 **권한을 넓히는 수단이 아니다.** 어떤 값이든 서버는 §2.2 판정을 통과한 기록만 반환한다.
- `scope=shared` 는 본인 기록을 제외한다. 본인 기록은 `mine` 에서 이미 전부 보이므로 중복을 피한다.
- `visibility` 와 공유 그룹 목록은 **요청자가 작성자인 기록에만** 응답에 포함한다.

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
      "author": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
      "distanceKm": 1.2,
      "createdAt": "2026-09-08T09:12:00Z"
    }
  ],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
}
```

**POST `/api/records` 요청 예시**
```json
{
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
  "visibility": "GROUP",
  "groupIds": [2, 5]
}
```

- `visibility` 생략 시 `PRIVATE` 이다.
- `groupIds` 는 `visibility=GROUP` 일 때만 의미가 있으며, 그 외 값일 때 함께 오면 무시한다.
- `groupIds` 가 빈 배열인 `GROUP` 기록은 허용한다. 작성자 외에는 아무도 볼 수 없는 상태이며,
  이를 프론트엔드가 안내한다 (공통 명세 §3.2).

**GET `/api/records/{id}` 응답 예시 (요청자가 작성자인 경우)**
```json
{
  "id": 3,
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
  "visibility": "GROUP",
  "sharedGroups": [
    { "id": 2, "name": "가족" },
    { "id": 5, "name": "제주 동행" }
  ],
  "createdAt": "2026-09-08T09:12:00Z",
  "updatedAt": "2026-09-08T09:12:00Z"
}
```

- 요청자가 작성자가 아니면 `isAuthor: false` 이며 `visibility` 와 `sharedGroups` 는 **응답에서 제외한다.**
  열람자에게 "이 기록이 어느 그룹에 공유되었는지"를 알릴 이유가 없다.

**PATCH `/api/records/{id}/visibility` 요청 예시**
```json
{ "visibility": "PRIVATE" }
```
```json
{ "visibility": "GROUP", "groupIds": [2] }
```
- 공유 그룹 목록은 **전체 교체**다. 빠진 그룹의 공유는 해제된다.
- `PRIVATE`/`PUBLIC` 으로 바꾸면 기존 `VisitRecordShare` 행을 모두 삭제한다 (§3.1).
- 좁히는 변경은 즉시 적용되어, 직전까지 열람 가능하던 사용자도 이후 요청에서 `404` 를 받는다.

### 4.3 장소 검색 (등록용, 네이버 지역 검색 집계)

기록 등록 폼에서 장소를 고를 때 사용하는 검색이다. 백엔드가 네이버 지역 검색 오픈API를 서버
사이드에서 호출·집계해 제공한다 (§1.5). API 키를 프론트엔드에 노출하지 않기 위해 반드시 백엔드를
경유한다. **이 API는 저장 단위가 아니라 외부 조회 결과를 돌려줄 뿐이며, 경로가 `/api/records` 와
분리된 이유도 그것이다.**

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/places/search?keyword={keyword}&lat={lat}&lng={lng}&page={page}&size=10` | 지역 검색 결과를 집계·정렬·페이지네이션하여 반환 | 선택 |

**동작 방식**
1. 원 검색어 및 보조 변형(지역명 결합 등)으로 **여러 번 호출**해 후보를 모으고 `(name, address)`
   기준으로 중복을 제거한다.
2. 각 후보의 `mapx`/`mapy` 를 WGS84로 변환한다 (§1.5).
3. `lat`/`lng` 가 주어지면 거리를 계산해 가까운 순으로 정렬하고, 없으면 원본 API 순서를 유지한다.
4. 집계된 전체 후보를 10건 단위로 페이지네이션한다.
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
  "page": 0, "size": 10, "totalElements": 4, "totalPages": 1
}
```

- 응답의 `category` 는 네이버가 내려주는 분류 문자열이며 서비스 카테고리(`SIGHT` 등)와 별개다.
- `distanceKm` 은 `lat`/`lng` 가 주어졌을 때만 포함된다.
- 사용자가 후보를 선택하면 프론트엔드가 해당 값을 그대로 `POST /api/records` 요청에 채워 넣는다.

### 4.4 사진

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/api/records/{id}/photos` | 사진 업로드 (multipart/form-data, 필드명 `files`, 다중 첨부 가능) | 작성자만 |
| DELETE | `/api/records/{id}/photos/{photoId}` | 사진 삭제 | 작성자만 |
| GET | `/api/files/photos/**` | 저장된 사진 파일 서빙(정적 리소스) | 선택 |

- 업로드 제약: 파일당 최대 5MB, 요청 합계 30MB, 허용 포맷 `image/jpeg`·`image/png`·`image/webp`.
  서버는 Content-Type/확장자를 검증하고 위반 시 `400 INVALID_FILE` 을 반환한다.
- **사진을 올리고 지울 수 있는 사람은 작성자뿐이다.** 이전 판의 "업로더 또는 등록자" 구분은
  기록이 개인 소유가 되면서 사라졌다.
- ⚠️ `GET /api/files/photos/**` 는 **공개 범위를 적용하지 않는다.** 경로를 아는 사람은 비공개
  기록의 사진도 볼 수 있으며, 현재는 추측 불가능한 UUID 경로에만 의존한다 (§8).

### 4.5 그룹

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/groups` | 내가 소유하거나 속한 그룹 목록 (`role: OWNER\|MEMBER` 포함) | 필요 |
| POST | `/api/groups` | 그룹 생성 (소유자를 멤버로 함께 입력) | 필요 |
| GET | `/api/groups/{id}` | 그룹 상세 (멤버 목록 포함) | 소유자·멤버 |
| PUT | `/api/groups/{id}` | 그룹 이름 변경 | 소유자만 |
| DELETE | `/api/groups/{id}` | 그룹 삭제 (멤버·초대·공유 관계 함께 삭제) | 소유자만 |
| DELETE | `/api/groups/{id}/members/{userId}` | 멤버 제외 | 소유자만 |
| DELETE | `/api/groups/{id}/members/me` | 그룹 탈퇴. 소유자는 `403` | 멤버 본인 |

**GET `/api/groups/{id}` 응답 예시**
```json
{
  "id": 2,
  "name": "가족",
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
- 멤버 정보는 이름과 프로필 사진까지만 내려준다. **이메일은 포함하지 않는다** (공통 명세 §6.7).
- 소유자 제외 요청(`DELETE .../members/{소유자 id}`)은 `400 VALIDATION_ERROR` 다.

### 4.6 초대

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/api/groups/{id}/invite` | 초대 링크 발급/재발급. 이전 토큰은 즉시 무효 | 소유자만 |
| GET | `/api/groups/{id}/invite` | 현재 유효한 초대 링크 조회. 없으면 `204 No Content` | 소유자만 |
| DELETE | `/api/groups/{id}/invite` | 초대 링크 폐기 | 소유자만 |
| GET | `/api/invites/{token}` | 초대 미리보기 (그룹명·초대자·만료 시각) | 선택 |
| POST | `/api/invites/{token}/accept` | 초대 수락 → 멤버로 가입 | 필요 |

**POST `/api/groups/{id}/invite` 응답 예시**
```json
{
  "token": "ZXhhbXBsZS10b2tlbi0xMjM0NTY",
  "expiresAt": "2026-09-24T09:12:00Z"
}
```
- 토큰은 추측 불가능한 난수(URL-safe, 최소 128비트)다. 프론트엔드가 이 값으로 초대 URL을 조립한다.
- **유효 기간은 발급 후 7일**이며, **그룹당 유효 토큰은 1개**다. 재발급하면 기존 행을 갱신해
  이전 토큰이 즉시 무효가 된다 (§3.1).
- 하나의 토큰으로 여러 명이 수락할 수 있다. 정원(5명)에 도달하면
  `409 GROUP_MEMBER_LIMIT_EXCEEDED` 다.
- `GET /api/invites/{token}` 은 비로그인도 호출할 수 있다. 로그인 전에 "무슨 그룹 초대인지"를
  보여줘야 하기 때문이며, 그룹명·초대자 이름·만료 시각만 내려주고 멤버 목록이나 기록은 포함하지 않는다.
- 이미 멤버인 사용자의 수락은 **오류가 아니다.** 멤버 상태를 그대로 두고 `200` 으로 응답한다
  (재클릭·중복 클릭이 실패처럼 보이지 않게 하기 위해서다).
- 만료된 토큰은 `410 INVITE_EXPIRED`, 없는 토큰은 `404 INVITE_NOT_FOUND` 다. 둘을 구분하는 이유는
  만료의 경우 "재발급을 요청하세요"라고 안내할 수 있기 때문이다.

### 4.7 태그

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/tags` | 태그 목록/자동완성 (쿼리 `keyword`) | 선택 |

- 자동완성 후보는 **요청자가 볼 수 있는 기록에 쓰인 태그**로 제한한다. 전체 태그를 그대로
  내려주면 비공개 기록에만 쓰인 태그가 노출되기 때문이다.

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
    fun store(file: MultipartFile, record: VisitRecord): StoredPhoto
    fun delete(storageKey: String)
    fun resolveUrl(storageKey: String): String
}
```

- `application.yml` 의 `app.storage.type=filesystem|s3` 설정값으로 구현체를 선택한다.
- API 응답의 `photos[].url` 은 저장 방식과 무관하게 항상 접근 가능한 URL을 반환하므로,
  프론트엔드는 저장소 전환과 무관하게 동작한다.
- S3 전환 시 **서명 URL(pre-signed URL)** 을 도입하면 §4.4의 공개 범위 미적용 문제를 함께
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
| 입력값 검증 실패 (필수값 누락, 카테고리 값 오류, 평점 범위·단위 오류) | 400 | `VALIDATION_ERROR` |
| 경로·쿼리 파라미터 타입 불일치, 필수 파라미터(`scope` 등) 누락, 본문 파싱 실패 | 400 | `VALIDATION_ERROR` |
| 허용되지 않는 파일 형식/용량 초과 (멀티파트 단계 초과 포함) | 400 | `INVALID_FILE` |
| 비로그인 사용자의 보호된 API 접근 | 401 | `UNAUTHENTICATED` |
| 권한 없는 수정/삭제 시도 (열람은 가능한 경우) | 403 | `FORBIDDEN` |
| 존재하지 않거나 **열람 권한이 없는** 기록 | 404 | `RECORD_NOT_FOUND` |
| 존재하지 않거나 접근 권한이 없는 그룹 | 404 | `GROUP_NOT_FOUND` |
| 존재하지 않는 사진 | 404 | `PHOTO_NOT_FOUND` |
| 존재하지 않는 초대 토큰 | 404 | `INVITE_NOT_FOUND` |
| 매핑되지 않은 주소, 존재하지 않는 정적 파일 | 404 | `NOT_FOUND` |
| 지원하지 않는 HTTP 메서드 | 405 | `METHOD_NOT_ALLOWED` |
| 만료된 초대 토큰 | 410 | `INVITE_EXPIRED` |
| 지원하지 않는 `Content-Type` | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 그룹 정원(5명) 초과 | 409 | `GROUP_MEMBER_LIMIT_EXCEEDED` |
| 그 밖의 DB 제약 위반 (동시 요청 경합 등) | 409 | `CONFLICT` |
| 네이버 지역 검색 오픈API 호출 실패/한도 초과 | 502 | `PLACE_SEARCH_UNAVAILABLE` |
| 그 밖의 처리되지 않은 예외 | 500 | `INTERNAL_ERROR` |

- `500` 과 `409` 의 `message` 는 고정 문구로 내려간다. 예외 메시지에 테이블·제약 이름이나 내부
  구조가 드러날 수 있어 응답에 싣지 않고 서버 로그로만 남긴다.
- `400` 의 `message` 는 어느 필드가 왜 틀렸는지 담는다 (예: `rating: 평점은 0.5점 단위로만 입력할 수 있습니다.`).
- **`404 RECORD_NOT_FOUND` 의 `message` 는 존재하지 않는 기록과 권한 없는 기록이 동일해야 한다.**
  문구가 다르면 그 차이만으로 기록의 존재가 드러난다.

## 7. 비기능 요구사항
- 목록 조회는 페이지네이션을 기본으로 하며, 응답에 `content`, `page`, `size`, `totalElements`,
  `totalPages` 를 포함한다.
- 좌표 기반 거리순 정렬(`sort=distance`)은 요청자의 기준 위치(`lat`,`lng` 쿼리 파라미터)로
  서버에서 계산한다. 이 좌표는 계산에만 쓰고 사용자와 묶어 저장하지 않는다 (공통 명세 §5).
- 모든 쓰기 API(`POST`/`PUT`/`PATCH`/`DELETE`)는 요청 바디 검증(Bean Validation)을 수행한다.
- 세션 쿠키는 `HttpOnly`, 운영 환경에서는 `Secure` 속성을 적용한다.
- 세션 쿠키 인증이므로 CSRF 보호를 유지한다. 서버는 `XSRF-TOKEN` 쿠키(JS 접근 가능)를 내려주고,
  프론트엔드는 쓰기 요청 시 그 값을 `X-XSRF-TOKEN` 헤더로 보낸다. 헤더가 없으면 `403` 이다.
- CORS: 프론트엔드 개발 서버(Vite, 기본 `http://localhost:5173`) 오리진과 자격 증명 포함 요청을 허용한다.
- 파일 업로드 최대 요청 크기는 `spring.servlet.multipart.max-request-size` 로 제한한다.
- **공개 범위 판정은 조회 쿼리 단계에서 수행한다.** 전체를 읽어 온 뒤 애플리케이션에서 걸러내면
  페이지네이션 건수가 어긋나고, 누락 시 곧바로 정보 유출이 된다.
- 초대 토큰은 `SecureRandom` 기반으로 생성한다. 순번·UUIDv1 등 추측 가능한 값은 쓰지 않는다.

## 8. 제약사항 및 향후 과제
- **`SecurityConfig` 의 `permitAll()` 을 §2.2 정책으로 되돌리는 작업이 남아 있다.** 공개 범위
  판정은 서비스·조회 계층에 구현되어 있으나, 인증 자체는 아직 컨트롤러가 `requireLogin` 으로 막는다.
- **스키마 마이그레이션 도구(Flyway/Liquibase)가 없다.** `schema.sql` 은 `CREATE TABLE IF NOT EXISTS`
  기반이라 이미 만들어진 테이블에 컬럼을 추가하지 못한다. 이번 개정으로 테이블 구조가 크게
  바뀌었으므로(기존 `places`/`reviews` 를 쓰던 개발 DB 는 재생성이 필요하다), 운영 데이터가
  생기기 전에 도구 도입이 선행되어야 한다.
- **사진 서빙에 공개 범위가 적용되지 않는다** (§4.4). 추측 불가능한 UUID 경로에 의존하는 상태이며,
  서명 URL 또는 인가 기반 서빙으로의 전환이 후속 과제다 (§5.2).
- **공동 편집은 범위 밖이다.** 그룹은 조회 전용이며, 멤버가 같은 기록을 수정하는 모델이 필요해지면
  편집 충돌 처리·이력 관리가 추가로 필요하다 ([`../REFERENCE.md`](../REFERENCE.md) 미결 사항 참고).
- **공개 링크(토큰 URL) 열람은 지원하지 않는다.** 비공개 기록을 링크로 여는 방식은 검토 후 제외했다.
- **태그(`/api/tags`)는 백엔드만 구현되어 있고 프론트엔드에 입력·필터 UI 가 없다.** 현재 등록 요청은
  항상 빈 태그 목록으로 들어온다.
- 네이버 지역 검색 오픈API의 (a) 정확한 `mapx`/`mapy` 좌표계, (b) 일일 호출 한도는 구현 착수
  시점에 developers.naver.com 공식 문서로 재검증해야 한다 (§1.5).
- §4.3의 "10건 페이지네이션 + 거리순 정렬" 장소 검색은 원본 API의 검색어당 5건 상한 때문에
  다중 호출 집계로도 충분한 후보 풀을 확보하지 못할 수 있다. 구현 착수 전 재검토하고, 필요 시
  카카오 로컬 API 등 다른 POI 데이터 소스 병행을 고려한다.
- 오브젝트 스토리지 전환 일정 및 기존 파일시스템 데이터 마이그레이션 절차는 후속 명세에서 다룬다.
