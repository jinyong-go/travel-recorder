# 여행 지도 (travel-recorder) 백엔드 요구사항 정의서

## 1. 개요

### 1.1 목적
사용자가 다녀온 여행 장소를 기록·공유하는 서비스의 백엔드 API를 정의한다. 사용자는 네이버 계정으로 로그인하여 여행 장소(이하 "여행지")를 등록하고, 등록된 여행지에 평점·댓글·사진을 추가할 수 있다. 여행지 등록 시 위치 입력은 백엔드가 프록시하는 네이버 장소 검색 결과를 이용한다.

### 1.2 범위
본 문서는 `backend/` (Kotlin + Spring Boot) 의 도메인 모델, 인증/인가 정책, REST API, 사진 저장소 설계를 정의한다. 프론트엔드 화면 요구사항은 [`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) 를 따른다.

### 1.3 용어

| 용어 | 설명 |
|---|---|
| 여행지 (Place) | 사용자가 등록하는 장소 1건 |
| 카테고리 | 관광지 / 쇼핑 / 맛집 중 하나 (단일 선택) |
| 태그 | 카테고리 외에 여행지를 설명하는 자유 형식 키워드 (다중 선택) |
| 등록자 | 여행지를 최초 등록한 사용자 |
| 평점 | 사용자별 1~5점 평가, 여행지에 종속 |
| 댓글 | 사용자가 여행지에 남기는 텍스트, 여행지에 종속 |
| 사진 | 여행지에 첨부된 이미지 파일, 여행지에 종속 |

### 1.4 현재 스캐폴딩 기준
- Kotlin 2.3 / Spring Boot 4.1 / Spring Data JPA / Spring Security OAuth2 Client / H2(dev)
- `SecurityConfig` 는 개발 편의를 위해 `anyRequest().permitAll()` 로 임시 비활성화되어 있음 (`SecurityConfig.kt:23`). 본 명세의 인가 정책 구현 시 인증이 필요한 경로부터 `authenticated()` 로 전환한다.
- `application.yml` 에는 Google, Naver OAuth 클라이언트가 모두 등록되어 있다. 본 명세는 **네이버 로그인만을 요구사항으로 다룬다** (§2.1). Naver 클라이언트 등록과 `UserService` 의 네이버 프로필 언래핑 로직은 이미 스캐폴딩에 구현되어 있으므로 재사용한다. Google 클라이언트 등록은 더 이상 요구사항이 아니며, 실제 코드에서 정리(제거) 대상이다 (§8).

### 1.5 네이버 API 사용 범위 확인
본 서비스가 사용하려는 "네이버 지도 API"는 실제로는 발급 경로와 제공 기능이 다른 두 제품으로 나뉜다. 둘 다 사용 가능하지만 역할이 다르므로 구분해서 도입한다.

| 제품 | 발급처 | 제공 기능 | 본 서비스에서의 용도 |
|---|---|---|---|
| NAVER Cloud Platform (NCP) Maps | ncloud.com (별도 콘솔 가입, 과금형) | Geocoding, Reverse Geocoding, Static/Dynamic Map(JS SDK), Directions | **지도 표시(임베드)** 는 프론트엔드가 NCP Client ID로 `naver.maps.Map` SDK를 직접 렌더링한다 (백엔드 개입 없음, [`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) §5.7). 좌표↔주소 변환이 필요하면 백엔드에서 Geocoder를 보조적으로 사용할 수 있다 |
| 네이버 검색 오픈API – 지역(Local) 검색 | developers.naver.com (무료 애플리케이션 등록) | 키워드로 장소/업체 검색 → `title, category, address, roadAddress, telephone, mapx, mapy` | **§4.2a 장소 검색 API 의 실제 구현체** (백엔드가 집계·가공 후 제공) |

- **키워드 기반 장소 검색(예: "성산일출봉" 검색 → 후보 목록)은 NCP Maps 단독으로는 제공되지 않는다.** 지역 검색 오픈API를 반드시 함께 사용해야 하며, 두 제품의 인증키(Client ID/Secret)는 서로 다르다.
- 지역 검색 오픈API는 **응답에 안정적인 장소 고유 ID가 없다** (Google Place ID 같은 필드 없음). 따라서 `Place` 에는 외부 장소 ID 대신 검색 결과의 주소·좌표·원본 링크만 저장한다 (§3, §4.2a).
- 지역 검색 오픈API는 **한 번의 호출당 최대 5건**까지만 반환하며(`display` 최대 5, `start` 사실상 1), **좌표/반경 기반 검색이나 거리순 정렬 파라미터를 지원하지 않고**, 브라우저에서 직접 호출할 수 없다(CORS 미지원) — 반드시 백엔드에서 프록시해야 한다. 일일 호출 한도는 구현 시점에 developers.naver.com 문서로 재확인한다.
- 응답 좌표(`mapx`, `mapy`)는 WGS84 경위도가 아닌 **정수형(10^7 배율) 좌표**로 내려온다고 프론트엔드 명세([`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) §5.2)에서도 동일하게 확인되었으므로, 이를 기준값으로 삼아 백엔드에서 `/10,000,000` 변환 후 저장·응답한다. 단, 실제 값이 다를 가능성에 대비해 구현 착수 시 표본 응답으로 한 번 더 검증한다.
- 프론트엔드는 장소 검색 결과를 **10건씩 페이지네이션, 기준 위치 거리순 정렬**로 요구한다(§4.2a 참고). 원본 API가 검색어당 5건으로 제한되므로, 이 요구사항은 **백엔드가 여러 검색어 변형으로 후보를 모아 집계**해야만 충족 가능하며, 그럼에도 확보 가능한 후보 풀 자체가 제한적일 수 있다(§8).

## 2. 인증 및 인가

### 2.1 로그인
- 로그인 수단은 **네이버 OAuth 2.0 하나만** 지원한다. 자체 회원가입/이메일·비밀번호 로그인, Google 로그인은 제공하지 않는다.
- Spring Security OAuth2 Client 의 Authorization Code 플로우를 사용한다.
  - 로그인 시작: `GET /oauth2/authorization/naver`
  - 콜백: `GET /login/oauth2/code/naver` (Spring Security 기본 처리, `application.yml` 에 이미 등록됨)
- 네이버는 사용자 프로필을 최상위 `response` 객체로 감싸서 반환하므로, `UserService`(`OAuth2UserService` 구현체)가 이를 평탄화하여 표준 속성 맵으로 노출한다 (이미 구현됨, `UserService.kt`). 사용자 식별자는 `response.id` 를 사용한다.
- 네이버 애플리케이션(개발자센터) 설정에서 필요한 권한(이메일 주소, 이름 또는 별명, 프로필 사진)을 "필수" 로 활성화해야 해당 정보를 받을 수 있다.
- 로그인 성공 시 서버는 세션(쿠키, `JSESSIONID`)을 발급한다. 프론트엔드는 이후 API 요청 시 쿠키를 포함(`credentials: 'include'`)하여 인증 상태를 유지한다.
- 최초 로그인한 네이버 계정은 `User` 로 자동 가입(Upsert) 처리하며, 이후 로그인은 `provider("naver") + providerId` 로 기존 사용자를 매칭한다.
- 로그아웃: `POST /api/auth/logout` — 세션을 무효화한다.

### 2.2 인가 정책

| 행위 | 비로그인 | 로그인 사용자(비등록자) | 등록자 본인 |
|---|:---:|:---:|:---:|
| 여행지 목록/상세 조회 | ✅ | ✅ | ✅ |
| 여행지 등록 | ❌ | ✅ | - |
| 여행지 수정 | ❌ | ❌ | ✅ |
| 여행지 삭제 | ❌ | ❌ | ✅ |
| 평점 등록/수정(본인 평점) | ❌ | ✅ | ✅ |
| 댓글 작성 | ❌ | ✅ | ✅ |
| 댓글 삭제 | ❌ | 작성자 본인만 | 작성자 본인만 |
| 사진 업로드 | ❌ | ✅ | ✅ |
| 사진 삭제 | ❌ | 업로더 본인 | 업로더 본인 또는 여행지 등록자 |

- 인증이 필요한 엔드포인트에 비로그인 상태로 접근하면 `401 Unauthorized` 를 반환한다.
- 권한이 없는 사용자가 수정/삭제를 시도하면 `403 Forbidden` 을 반환한다 (예: 등록자가 아닌 사용자의 여행지 삭제 시도, 작성자가 아닌 사용자의 댓글 삭제 시도).

## 3. 도메인 모델

```
User
  id: Long (PK)
  provider: String            // "naver" (고정)
  providerId: String          // 네이버 회원번호 (response.id)
  email: String
  name: String
  profileImageUrl: String?
  createdAt: Instant

Place
  id: Long (PK)
  owner: User (FK)            // 등록자
  name: String
  category: Category          // SIGHT | SHOPPING | FOOD
  roadAddress: String?        // 네이버 지역 검색 결과의 지번/도로명 주소 그대로 보관 (검색 재현용)
  externalLink: String?       // 네이버 지역 검색 결과 원본 링크(link 필드). 안정적 장소 ID가 없어 참고용으로만 사용
  latitude: Double
  longitude: Double
  address: String
  memo: String?
  createdAt: Instant
  updatedAt: Instant

Tag
  id: Long (PK)
  name: String (unique)

PlaceTag                      // Place - Tag 다대다 조인
  place: Place (FK)
  tag: Tag (FK)

Photo
  id: Long (PK)
  place: Place (FK)
  uploader: User (FK)
  storageKey: String          // 저장소 내 식별 경로/키 (파일시스템 상대경로 또는 향후 S3 key)
  originalFileName: String
  contentType: String
  fileSizeBytes: Long
  createdAt: Instant

Rating
  id: Long (PK)
  place: Place (FK)
  user: User (FK)
  score: Int                  // 1~5
  createdAt: Instant
  updatedAt: Instant
  // unique(place_id, user_id) — 사용자당 여행지 1개 평점만 허용, 재평가 시 갱신(upsert)

Comment
  id: Long (PK)
  place: Place (FK)
  author: User (FK)
  content: String
  createdAt: Instant
```

### 3.1 제약 조건
- `Place.category` 는 `SIGHT`(관광지) / `SHOPPING`(쇼핑) / `FOOD`(맛집) 3개 값 중 하나만 허용한다.
- `Place.latitude`, `Place.longitude` 는 필수이며, 지도 표시에 그대로 사용할 수 있는 WGS84 위경도 정밀도로 저장한다 (소수점 6자리 이상 권장). 네이버 지역 검색 결과의 `mapx`/`mapy` 는 저장 전 WGS84로 변환한다 (§1.5).
- 안정적인 외부 장소 ID가 없으므로, 동일 장소 중복 등록 여부는 좌표(근접 반경) 또는 `(name, address)` 조합으로 애플리케이션 레벨에서 판단한다 (DB 유니크 제약은 두지 않는다).
- `Place` 삭제 시 연관된 `PlaceTag`, `Photo`, `Rating`, `Comment` 는 모두 함께 삭제된다 (cascade).
- `Rating` 은 `(place_id, user_id)` 유니크 제약으로 사용자당 1개만 존재하며, 재평가 요청은 upsert(있으면 갱신, 없으면 생성) 로 처리한다.
- `Tag` 는 이름 중복 없이 재사용되며(다른 여행지가 같은 태그를 공유 가능), 존재하지 않는 태그명이 등록 요청에 포함되면 서버가 신규 생성한다.

## 4. API 명세

기본 경로: `/api`. 응답은 JSON. 목록 조회는 페이지네이션을 지원한다 (`page`(0-base), `size`, 필요시 `sort`).

### 4.1 인증

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/oauth2/authorization/naver` | 네이버 로그인 시작 (리다이렉트) | - |
| GET | `/api/auth/me` | 현재 로그인 사용자 정보 조회. 비로그인 시 `401` | 선택 |
| POST | `/api/auth/logout` | 로그아웃, 세션 무효화 | 필요 |

### 4.2 여행지

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/places` | 목록 조회. 쿼리: `category`, `tag`, `keyword`(장소명/지역 검색), `sort`(`recent`\|`rating`\|`distance`), `lat`,`lng`(거리순 정렬 시), `page`, `size` | 선택 |
| GET | `/api/places/{id}` | 상세 조회 (태그, 사진, 평균 평점, 평점 수 포함) | 선택 |
| POST | `/api/places` | 여행지 등록 | 필요 |
| PUT | `/api/places/{id}` | 여행지 수정 | 등록자만 |
| DELETE | `/api/places/{id}` | 여행지 삭제 (종속 사진/평점/댓글 함께 삭제) | 등록자만 |

### 4.2a 장소 검색 (등록용, 네이버 지역 검색 집계)

여행지 등록 폼에서 위치를 입력할 때 사용하는 검색으로, 백엔드가 네이버 지역 검색 오픈API를 서버 사이드에서 호출·집계해 결과를 제공한다 (§1.5). API 키를 프론트엔드에 노출하지 않기 위해 반드시 백엔드를 경유한다. 프론트엔드 요구사항([`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) §5.2)에 맞춰 **10건 페이지네이션 + 기준 위치 거리순 정렬**을 응답 형태로 제공한다.

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/places/search?keyword={keyword}&lat={lat}&lng={lng}&page={page}&size=10` | 네이버 지역 검색 결과를 집계·정렬·페이지네이션하여 반환. `lat`/`lng` 는 프론트엔드의 기준 위치(§5.3, 클라이언트 로컬 값) | 선택 |

**동작 방식**
1. 네이버 지역 검색 오픈API는 검색어당 최대 5건만 반환하므로, 백엔드는 원 검색어 및 보조 변형(지역명 결합 등)으로 **여러 번 호출**해 후보를 모으고 `(name, address)` 기준으로 중복을 제거한다.
2. 각 후보의 `mapx`/`mapy` 를 WGS84로 변환한다 (§1.5).
3. `lat`/`lng` 가 주어지면 후보와의 거리를 계산해 가까운 순으로 정렬하고, 없으면 원본 API의 반환 순서를 유지한다.
4. 집계된 전체 후보 목록을 10건 단위로 페이지네이션하여 반환한다.
5. 원본 API 자체가 검색어당 5건으로 제한되므로, 다건 확보에 실패하면 확보된 만큼만 반환한다(전체 후보가 10건 미만일 수 있음). 이 경우 `totalElements` 로 실제 확보된 건수를 그대로 알려준다.

**GET `/api/places/search?keyword=제주 카페&lat=33.45&lng=126.56&page=0&size=10` 응답 예시**
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
  "page": 0,
  "size": 10,
  "totalElements": 4,
  "totalPages": 1
}
```
- 검색 결과는 저장 전 상태이며, 사용자가 후보를 선택하면 프론트엔드가 해당 값을 그대로 `POST /api/places` 요청에 채워 넣는다.
- `latitude`/`longitude` 는 네이버 응답의 `mapx`/`mapy` 를 백엔드에서 WGS84로 변환한 값이다.
- `distanceKm` 은 `lat`/`lng` 파라미터가 주어졌을 때만 포함된다.

**POST `/api/places` 요청 예시**
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
  "memo": "일출 명소, 아침 일찍 방문 추천"
}
```

**GET `/api/places/{id}` 응답 예시**
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
  "memo": "일출 명소, 아침 일찍 방문 추천",
  "photos": [
    { "id": 11, "url": "/api/files/photos/2026/09/14/abc123.jpg" }
  ],
  "rating": { "average": 4.9, "count": 128 },
  "owner": { "id": 7, "name": "홍길동", "profileImageUrl": "https://..." },
  "createdAt": "2026-09-08T09:12:00Z",
  "updatedAt": "2026-09-08T09:12:00Z"
}
```

### 4.3 태그

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/tags` | 태그 목록/자동완성 (쿼리 `keyword`) | 선택 |

### 4.4 평점

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/places/{id}/ratings/me` | 로그인 사용자의 본인 평점 조회 | 필요 |
| PUT | `/api/places/{id}/ratings` | 본인 평점 등록/수정 (upsert). Body: `{ "score": 5 }` | 필요 |
| DELETE | `/api/places/{id}/ratings` | 본인 평점 삭제 | 작성자만 |

### 4.5 댓글

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/places/{id}/comments` | 댓글 목록 (최신순, 페이지네이션) | 선택 |
| POST | `/api/places/{id}/comments` | 댓글 작성. Body: `{ "content": "..." }` | 필요 |
| DELETE | `/api/places/{id}/comments/{commentId}` | 댓글 삭제 | 작성자만 |

### 4.6 사진

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/api/places/{id}/photos` | 사진 업로드 (multipart/form-data, 필드명 `files`, 다중 첨부 가능) | 필요 |
| DELETE | `/api/places/{id}/photos/{photoId}` | 사진 삭제 | 업로더 또는 여행지 등록자 |
| GET | `/api/files/photos/**` | 저장된 사진 파일 서빙(정적 리소스). 향후 S3 전환 시 이 경로 대신 S3 URL(또는 CDN URL)을 직접 반환하도록 변경 | 선택 |

- 업로드 제약: 파일당 최대 5MB, 허용 포맷 `image/jpeg`, `image/png`, `image/webp`. 서버는 Content-Type/확장자를 검증하고 위반 시 `400 Bad Request` 를 반환한다.
- 한 번의 요청으로 다중 파일 업로드를 허용한다 (여행지당 사진 개수 제한은 향후 과제로 둔다).

## 5. 사진 저장소 설계

### 5.1 현재 단계 — 서버 파일시스템
- 업로드된 파일은 서버 로컬 디스크의 설정된 루트 경로(예: `app.storage.local.root-dir`) 아래에 `yyyy/MM/dd/{UUID}.{ext}` 형태로 저장한다.
- `Photo.storageKey` 에는 저장소 루트 기준 상대 경로를 저장한다 (예: `2026/09/14/abc123.jpg`).
- 파일은 `GET /api/files/photos/**` 정적 리소스 핸들러(또는 별도 컨트롤러)를 통해 서빙한다.

### 5.2 향후 전환 — AWS S3
- `PhotoStorageService` 인터페이스로 저장 로직을 추상화하여 구현체를 `FileSystemPhotoStorageService`(현재) / `S3PhotoStorageService`(향후) 로 교체 가능하게 설계한다.

```kotlin
interface PhotoStorageService {
    fun store(file: MultipartFile, place: Place): StoredPhoto
    fun delete(storageKey: String)
    fun resolveUrl(storageKey: String): String
}
```

- `application.yml` 의 `app.storage.type=filesystem|s3` 설정값으로 구현체를 선택하도록(예: `@ConditionalOnProperty`) 구성한다.
- S3 전환 시 `Photo.storageKey` 는 S3 object key 로, `resolveUrl` 은 S3(또는 CloudFront) URL을 반환하도록 변경한다. 기존에 파일시스템에 저장된 데이터의 마이그레이션(파일 업로드 + `storageKey` 갱신)은 별도 배치 작업으로 처리한다.
- API 응답의 `photos[].url` 필드는 저장 방식과 무관하게 항상 접근 가능한 URL을 반환하므로, 프론트엔드는 저장소 전환 여부와 상관없이 동일하게 동작한다.

## 6. 오류 응답 형식

공통 오류 응답 포맷 (`@ControllerAdvice` 기반):

```json
{
  "code": "PLACE_NOT_FOUND",
  "message": "존재하지 않는 여행지입니다.",
  "status": 404
}
```

| 상황 | HTTP 상태 | code 예시 |
|---|---|---|
| 비로그인 사용자의 보호된 API 접근 | 401 | `UNAUTHENTICATED` |
| 권한 없는 수정/삭제 시도 | 403 | `FORBIDDEN` |
| 존재하지 않는 리소스 | 404 | `PLACE_NOT_FOUND`, `COMMENT_NOT_FOUND` 등 |
| 입력값 검증 실패 (필수값 누락, 카테고리 값 오류, 평점 범위 초과 등) | 400 | `VALIDATION_ERROR` |
| 허용되지 않는 파일 형식/용량 초과 | 400 | `INVALID_FILE` |
| 네이버 지역 검색 오픈API 호출 실패/한도 초과 | 502 또는 429 | `PLACE_SEARCH_UNAVAILABLE` |

## 7. 비기능 요구사항
- 목록/댓글 조회는 페이지네이션을 기본으로 하며, 응답에 `content`, `page`, `size`, `totalElements`, `totalPages` 를 포함한다.
- 좌표 기반 거리순 정렬(`sort=distance`)은 요청자의 현재 위치(`lat`,`lng` 쿼리 파라미터)를 기준으로 서버에서 계산한다.
- 모든 쓰기 API(`POST`/`PUT`/`DELETE`)는 요청 바디 검증(Bean Validation)을 수행한다.
- 세션 쿠키는 `HttpOnly`, 운영 환경에서는 `Secure` 속성을 적용한다.
- 세션 쿠키 인증이므로 CSRF 보호를 유지한다. 서버는 `XSRF-TOKEN` 쿠키(JS 접근 가능)를 내려주고, 프론트엔드는 쓰기 요청(`POST`/`PUT`/`DELETE`) 시 그 값을 `X-XSRF-TOKEN` 헤더로 함께 보낸다. 헤더가 없으면 `403` 이 반환된다.
- CORS: 프론트엔드 개발 서버(Vite, 기본 `http://localhost:5173`) 오리진을 허용하고, 자격 증명(쿠키) 포함 요청을 허용한다.
- 파일 업로드 최대 요청 크기는 `spring.servlet.multipart.max-request-size` 로 제한한다.

## 8. 제약사항 및 향후 과제
- 운영 환경 데이터베이스(H2 대체, 예: PostgreSQL/MySQL)는 본 명세 범위 밖이며 별도 결정이 필요하다.
- `SecurityConfig` 의 `permitAll()` 임시 설정을 본 명세의 인가 정책(2.2절)에 맞춰 되돌리는 작업이 필요하다.
- `application.yml` 의 Google OAuth 클라이언트 등록(`spring.security.oauth2.client.registration.google`)은 더 이상 요구사항이 아니므로 코드에서 제거 대상이다. 네이버는 이미 등록되어 있어 추가 작업 없이 재사용 가능하다.
- 네이버 지역 검색 오픈API의 (a) 정확한 `mapx`/`mapy` 좌표계, (b) 현재 일일 호출 한도는 구현 착수 시점에 developers.naver.com 공식 문서로 재검증해야 한다 (§1.5).
- 지역 검색 오픈API는 안정적인 장소 ID를 제공하지 않으므로, 동일 장소 중복 등록 방지·재조회 등 "ID 기반" 시나리오가 필요해지면 NCP Maps Geocoding 또는 자체 좌표 클러스터링으로 보완하는 방안을 후속 검토한다.
- 지도 "표시(임베드)"는 네이버 지도(NCP Maps JS SDK)로 전환하기로 확정되었다([`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) §5.7). 이는 프론트엔드가 NCP Client ID로 직접 렌더링하므로 백엔드 API 변경은 없으나, NCP Maps 인증키(Client ID) 발급이 선행되어야 한다.
- §4.2a의 "10건 페이지네이션 + 거리순 정렬" 장소 검색은 원본 지역 검색 API의 검색어당 5건 상한 때문에 백엔드의 다중 호출 집계로도 충분한 후보 풀을 확보하지 못할 수 있다. 실제 구현 착수 전 이 제약이 서비스 요구 수준을 충족하는지 재검토가 필요하며, 필요 시 카카오 로컬 API 등 다른 POI 데이터 소스 병행을 고려한다 ([`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) §9와 동일한 결론).
- S3 전환 일정 및 기존 파일시스템 데이터 마이그레이션 절차는 후속 명세에서 다룬다.
- 여행지 수정 API는 본 명세에 포함하였으나, 프론트엔드 수정 UI는 [`frontend/SPECIFICATION.md`](../frontend/SPECIFICATION.md) 상 향후 과제로 명시되어 있어 화면 연동은 후속 범위이다.
