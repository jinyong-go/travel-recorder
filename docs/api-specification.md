# 여행 지도 (travel-recorder) API 명세서

## 1. 개요

`backend/` 가 제공하는 **REST API 계약**(경로·메서드·파라미터, 요청·응답 스키마, 페이지네이션, 오류 응답)을
정의한다. 도메인 규칙은 [`specification.md`](./specification.md)(이하 공통 명세)가 소유하며 **충돌 시 공통
명세가 우선한다.** 엔티티·인가 구현은 [`backend-specification.md`](./backend-specification.md)(이하 백엔드
명세), 화면은 [`frontend-specification.md`](./frontend-specification.md) 를 따른다.

## 2. API 목록

### 2.1 인증

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/api/auth/login` | 임시 로그인 (§2.1.1) | - |
| GET | `/oauth2/authorization/naver` | 네이버 로그인 시작 (§2.1.2) | - |
| GET | `/api/auth/session` | 세션 상태 조회 (§2.1.3) | - |
| GET | `/api/auth/me` | 내 정보 조회 (§2.1.4) | 선택 |
| GET | `/api/auth/me/login-history` | 로그인 이력 조회 (§2.1.5) | 필요 |
| POST | `/api/auth/logout` | 로그아웃 (§2.1.6) | 필요 |

#### 2.1.1 임시 로그인

`POST /api/auth/login`

- **임시** 인메모리 로그인이다. `local`·`dev` 전용이다 (백엔드 명세 §2.1).

#### 2.1.2 네이버 로그인 시작

`GET /oauth2/authorization/naver`

- 네이버 로그인으로 리다이렉트한다. **현재 비활성**이다 (백엔드 명세 §2.1).

#### 2.1.3 세션 상태 조회

`GET /api/auth/session`

- 클라이언트 부팅용이다. `{ authenticated, csrfToken }` 을 반환하며 비로그인도 `200` 이다 (백엔드 명세 §5).

#### 2.1.4 내 정보 조회

`GET /api/auth/me`

- 현재 로그인 사용자 정보를 반환한다. 비로그인 시 `401` 이다.

#### 2.1.5 로그인 이력 조회

`GET /api/auth/me/login-history?page=`

- **내 로그인 이력**이다 (백엔드 명세 §2.1).
- **경로에 사용자 id 를 받지 않는다.** 대상은 언제나 세션의 사용자이므로 남의 이력을 지목할 방법
  자체가 없다. 정렬은 `loggedInAt DESC` 다.

**응답 예시**
```json
{
  "content": [
    {
      "id": 120,
      "loggedInAt": "2026-10-09T09:12:00Z",
      "ipAddress": "203.0.113.7",
      "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ..."
    }
  ],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1
}
```

#### 2.1.6 로그아웃

`POST /api/auth/logout`

- 세션을 무효화한다.

### 2.2 여행

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/trips` | 여행 목록 조회 (§2.2.1) | 선택 |
| GET | `/api/trips/{id}` | 여행 상세 조회 (§2.2.2) | 선택 |
| POST | `/api/trips` | 여행 생성 (§2.2.3) | 필요 |
| PUT | `/api/trips/{id}` | 여행 수정 (§2.2.4) | 소유자만 |
| PATCH | `/api/trips/{id}/visibility` | 공개 범위 변경 (§2.2.5) | 소유자만 |
| PATCH | `/api/trips/{id}/cover` | 커버 사진 지정 (§2.2.6) | 소유자만 |
| DELETE | `/api/trips/{id}` | 여행 삭제 (§2.2.7) | 소유자만 |

#### 2.2.1 여행 목록 조회

```
GET /api/trips?scope=mine&keyword=제주&sort=recent&page=0
```

| 파라미터 | 값 | 설명 |
|---|---|---|
| `scope` | `mine` \| `shared` \| `public` | **필수.** 조회 범위 (§3.1) |
| `keyword` | 문자열 | 여행 이름 부분 일치 |
| `sort` | `recent`(기본) \| `startDate` | `recent` 는 생성 시각 역순, `startDate` 는 시작일 역순 |
| `page` | 정수 | 0-base, 기본 `0`. 페이지 크기는 10 고정 (§3.2) |

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
      "memo": "가족들과 다녀온 첫 제주",
      "coverPhotoUrl": "/api/files/photos/3f2b8c1e-7a4d-4e0b-9c55-1d2e6f8a9b07",
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

- `recordCount` 는 삭제되지 않은 하위 기록 수다. 0 일 수 있다 (백엔드 명세 §3.1).
- 목록 항목은 상세 응답에서 `updatedAt` 만 뺀 모양이다. `memo` 는 카드가 두 줄까지 보여주므로
  함께 내려준다 (frontend §4.4).
- `coverPhotoUrl` 은 커버가 지정되지 않았으면 `null` 이다. **서버는 대체 이미지를 고르지 않는다** —
  무엇을 대신 보여줄지는 화면의 판단이다.

#### 2.2.2 여행 상세 조회

`GET /api/trips/{id}`

- 열람 권한이 없으면 `404` 다.

**응답 예시 (요청자가 소유자인 경우)**
```json
{
  "id": 12,
  "name": "제주 3박 4일",
  "startDate": "2026-09-05",
  "endDate": "2026-09-08",
  "headcount": 4,
  "budget": 1250000,
  "memo": "가족들과 다녀온 첫 제주",
  "coverPhotoUrl": "/api/files/photos/3f2b8c1e-7a4d-4e0b-9c55-1d2e6f8a9b07",
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
  (§2.3.1). 여행당 기록 수에 제한이 없어 상세 응답이 무한정 커지는 것을 막기 위해서다.

#### 2.2.3 여행 생성

`POST /api/trips`

**요청 예시**
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
  아직 존재할 수 없기 때문이다 (§2.2.6).

#### 2.2.4 여행 수정

`PUT /api/trips/{id}`

- 여행 기본 정보를 수정한다. 공개 범위는 이 요청으로 바꾸지 않는다 (§2.2.5).

#### 2.2.5 공개 범위 변경

`PATCH /api/trips/{id}/visibility`

- 공개 범위와 공유 그룹만 변경한다.

**요청 예시**
```json
{ "visibility": "PRIVATE" }
```
```json
{ "visibility": "GROUP", "groupIds": [2, 5] }
```

- 공유 그룹 목록은 **전체 교체**다. 빠진 그룹의 공유는 해제된다.
- `PRIVATE`/`PUBLIC` 으로 바꾸면 기존 `TripShare` 행을 모두 삭제한다 (백엔드 명세 §3.1).
- 좁히는 변경은 즉시 적용되어, 직전까지 열람 가능하던 사용자는 이후 **여행과 하위 기록 전부에
  대해** `404` 를 받는다.

#### 2.2.6 커버 사진 지정

`PATCH /api/trips/{id}/cover`

**요청 예시**
```json
{ "photoId": "3f2b8c1e-7a4d-4e0b-9c55-1d2e6f8a9b07" }
```
```json
{ "photoId": null }
```

- `photoId` 는 **그 여행의 하위 기록에 속한 사진**이어야 한다. 다른 여행의 사진이거나 존재하지
  않으면 `404 PHOTO_NOT_FOUND` 다 (존재 은닉).
- `null` 을 보내면 커버 지정이 해제된다.
- 커버 지정은 사진 업로드 이후에만 가능하다 (공통 명세 §6.2).

#### 2.2.7 여행 삭제

`DELETE /api/trips/{id}`

- 하위 기록도 함께 soft delete 한다.

### 2.3 기록

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/records` | 기록 목록 조회 (§2.3.1) | 선택 |
| GET | `/api/records/{id}` | 기록 상세 조회 (§2.3.2) | 선택 |
| POST | `/api/records` | 기록 등록 (§2.3.3) | 여행 소유자만 |
| PUT | `/api/records/{id}` | 기록 수정 (§2.3.4) | 소유자만 |
| PATCH | `/api/records/{id}/trip` | 소속 여행 변경 (§2.3.5) | 소유자만 |
| DELETE | `/api/records/{id}` | 기록 삭제 (§2.3.6) | 소유자만 |

- **`PATCH /api/records/{id}/visibility` 는 폐기되었다.** 공개 범위는 기록에 없으므로
  `PATCH /api/trips/{id}/visibility` 로 대체된다 (§2.2.5).

#### 2.3.1 기록 목록 조회

```
GET /api/records?scope=mine&tripId=12&category=FOOD&tag=제주&keyword=카페
                &sort=recent&lat=37.55&lng=126.97&page=0
```

| 파라미터 | 값 | 설명 |
|---|---|---|
| `scope` | `mine` \| `shared` \| `public` | 조회 범위 (§3.1). **`tripId` 가 없으면 필수**, 있으면 생략할 수 있다 |
| `tripId` | 정수 | 특정 여행의 하위 기록으로 한정. 여행 상세 화면이 이 경우다 |
| `category` | `SIGHT`\|`SHOPPING`\|`FOOD` | 생략 시 전체 |
| `tag`, `keyword` | 문자열 | 태그 일치, 장소명·주소 부분 일치 |
| `sort` | `recent`(기본) \| `oldest` \| `rating` \| `distance` | `oldest` 는 등록순(여행 상세), `rating` 은 기록의 평점 기준, `distance` 는 기준 위치에서 가까운 순(같으면 최신순)이며 **페이지를 넘어 이어진다** |
| `lat`, `lng` | 실수 | `sort=distance` 일 때 필수이며 없으면 `400 VALIDATION_ERROR`. 요청자의 기준 위치 |
| `page` | 정수 | 0-base, 기본 `0`. 페이지 크기는 10 고정 (§3.2) |

- **범위 판정은 모두 소속 여행을 조인해서 한다** (백엔드 명세 §2.2, §2.1). 기록 테이블만 봐서는 판정할 수 없다.
- `tripId` 로 지정한 여행을 볼 수 없으면 `scope` 유무와 관계없이 `404 TRIP_NOT_FOUND` 다. 여행
  상세 조회(§2.2.2)와 같은 판정이다.
- **`tripId` 만 주고 `scope` 를 생략하면** 그 여행의 하위 기록 전부다. 열람자는 남의 여행이
  공유인지 공개인지 모르므로(`visibility` 는 소유자에게만 내려간다) 화면이 `scope` 를 고를 수 없다.
  볼 수 있는 여행이면 하위 기록도 전부 볼 수 있으므로(공통 명세 §3.5) 권한이 넓어지지 않는다.
- `scope` 와 `tripId` 를 함께 주었는데 어긋나면(예: `scope=mine` 인데 타인의 공개 여행) 오류가 아니라
  **빈 목록**을 반환한다.
- `scope` 와 `tripId` 가 둘 다 없으면 `400 VALIDATION_ERROR` 다.
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
      "thumbnailUrl": "/api/files/photos/3f2b8c1e-7a4d-4e0b-9c55-1d2e6f8a9b07",
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
- `author` 는 `trip.owner` 를 그대로 옮긴 값이다 (백엔드 명세 §3.1).

#### 2.3.2 기록 상세 조회

`GET /api/records/{id}`

- 열람 권한이 없으면 `404` 다.

**응답 예시 (요청자가 소유자인 경우)**
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
    { "id": "3f2b8c1e-7a4d-4e0b-9c55-1d2e6f8a9b07", "url": "/api/files/photos/3f2b8c1e-7a4d-4e0b-9c55-1d2e6f8a9b07" }
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

#### 2.3.3 기록 등록

`POST /api/records`

**요청 예시**
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

- **`tripId` 는 필수다.** 생략하면 `400 VALIDATION_ERROR` 다. 요청자가 소유하지 않은 여행일 때의
  응답은 백엔드 명세 §2.2.2 를 따른다.
- **`visibility` 와 `groupIds` 는 이 요청에 없다.** 공개 범위는 여행이 이미 갖고 있다 (§2.2).
- 장소명·주소·도로명주소·원본 링크의 길이, 원본 링크의 스킴, 태그 개수·길이 상한은 공통 명세
  §3.3 이 유일한 출처다. 위반하면 `400 VALIDATION_ERROR` 다.
  태그 개수는 요청에 담긴 그대로 센다(중복·공백 항목 포함). 저장할 때는 공백을 지우고 같은 이름을 합친다.

#### 2.3.4 기록 수정

`PUT /api/records/{id}`

- 전체 갱신이다. 입력값 상한과 검증 실패 응답은 기록 등록과 같다 (§2.3.3).

#### 2.3.5 소속 여행 변경

`PATCH /api/records/{id}/trip`

**요청 예시**
```json
{ "tripId": 15 }
```

- 기록을 다른 여행으로 옮긴다. 대상 여행은 **요청자가 소유한 여행**이어야 하며, 실패 응답은
  기록 등록과 같다 (백엔드 명세 §2.2.2).
- 옮기는 즉시 그 기록의 공개 범위는 새 여행의 것이 된다. **범위를 넓히는 이동일 수 있으므로**
  프론트엔드가 결과를 확인시킨다 (공통 명세 §3.5).
- 기록에 달린 사진이 이전 여행의 커버로 지정되어 있었다면 그 커버 지정은 해제된다 —
  커버는 자기 여행의 사진만 가리킬 수 있기 때문이다 (백엔드 명세 §3.1).

#### 2.3.6 기록 삭제

`DELETE /api/records/{id}`

- soft delete 한다.

### 2.4 장소 검색

기록 등록 폼에서 장소를 고를 때 사용하는 검색이다. 백엔드가 NAVER API HUB 지역 검색을 서버
사이드에서 호출·집계해 제공한다 (백엔드 명세 §1.3). API 키를 프론트엔드에 노출하지 않기 위해 반드시 백엔드를
경유한다. **이 API는 저장 단위가 아니라 외부 조회 결과를 돌려줄 뿐이며, 경로가 `/api/records` 와
분리된 이유도 그것이다.**

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/places/search` | 장소 검색 (§2.4.1) | 필요 |

#### 2.4.1 장소 검색

`GET /api/places/search?keyword={keyword}&page={page}`

- 네이버 지역 검색 결과를 집계·페이지네이션하여 반환한다. 비로그인은 `401` 이다.

**동작 방식**
1. 원 검색어로 한 번 호출해 후보를 모으고 `(name, address)` 기준으로 중복을 제거한다.
   보조 변형(지역명 결합 등)으로 여러 번 호출해 후보를 늘리는 집계는 후속 과제다 (백엔드 명세 §6.2).
2. 각 후보의 `mapx`/`mapy` 를 WGS84로 변환한다 (백엔드 명세 §1.3).
3. 원본 API 순서를 유지한다. 기준 위치를 받지 않으므로 거리 계산·정렬은 하지 않는다 (공통 명세 §4.2).
4. 집계된 전체 후보를 **5건 단위**로 페이지네이션한다. 원본 API가 호출당 5건까지만 주므로
   페이지 크기를 그보다 키워도 채울 수 없다 (§3.2).
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
      "link": "https://map.naver.com/..."
    }
  ],
  "page": 0, "size": 5, "totalElements": 4, "totalPages": 1
}
```

- 응답의 `category` 는 네이버가 내려주는 분류 문자열이며 서비스 카테고리(`SIGHT` 등)와 별개다.
- 사용자가 후보를 선택하면 프론트엔드가 해당 값을 그대로 `POST /api/records` 요청에 채워 넣는다.

### 2.5 사진

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/api/records/{id}/photos` | 사진 업로드 (§2.5.1) | 소유자만 |
| DELETE | `/api/records/{id}/photos/{photoId}` | 사진 삭제 (§2.5.2) | 소유자만 |
| GET | `/api/files/photos/{photoId}` | 사진 조회 (§2.5.3) | 선택 |

#### 2.5.1 사진 업로드

`POST /api/records/{id}/photos`

- multipart/form-data 이며 필드명은 `files` 다. 여러 장을 한 번에 첨부할 수 있다.
- 업로드 제약(크기·포맷)은 **공통 명세 §6.3이 유일한 출처**이며 양쪽 모듈이 같은 값을 쓴다.
  백엔드는 이를 설정값으로 외부화한다.
  서버는 Content-Type 을 검증하고, **파일 앞부분의 시그니처가 선언된 형식과 일치하는지도 확인한다.**
  선언만 이미지이고 내용은 다른 파일을 막기 위해서다. 위반 시 `400 INVALID_FILE` 을 반환한다.
  시그니처를 아는 형식은 JPEG·PNG·WebP 뿐이라, 허용 형식 설정에 다른 형식을 더해도 시그니처
  확인을 함께 추가하기 전까지는 거부된다.
- 원본 파일명은 255자를 넘으면 잘라 저장한다. 표시용 값일 뿐이라 업로드를 거부할 이유가 없다.
- **사진을 올리고 지울 수 있는 사람은 소속 여행의 소유자뿐이다.** 실패 시 상태 코드는 기록 편집과
  같다 (백엔드 명세 §2.2.2).

#### 2.5.2 사진 삭제

`DELETE /api/records/{id}/photos/{photoId}`

- 권한과 실패 응답은 업로드와 같다 (§2.5.1).
- 삭제된 사진이 어느 여행의 커버였다면 그 여행의 `cover_photo_id` 는 `NULL` 이 된다. 외래키가 없어
  사진 삭제 처리가 직접 해제해야 한다 (백엔드 명세 §3).
  커버 지정 자체는 `PATCH /api/trips/{id}/cover` 로 한다 (§2.2.6).

#### 2.5.3 사진 조회

`GET /api/files/photos/{photoId}`

- 사진 바이너리를 내려준다.
- 사진 id 는 UUID 문자열이다. 응답의 `url` 이 곧 `/api/files/photos/{photoId}` 이며, 클라이언트는
  URL 을 직접 조립하지 않고 응답 값을 그대로 쓴다.
- 저장된 `contentType` 을 `Content-Type` 으로 내려준다. 사진은 한 번 올라가면 내용이
  바뀌지 않으므로(수정 API 가 없다) 오래 캐시해도 된다. 없는 사진이면 `404 PHOTO_NOT_FOUND` 다.
  삭제된 여행·기록의 사진도 바이너리가 지워져 같은 `404` 다 (백엔드 명세 §3.2).
- ⚠️ **공개 범위를 적용하지 않는다.** URL 을 아는 사람은 비공개 여행의 사진도 볼 수 있으며, 현재는
  추측 불가능한 UUID 식별자에만 의존한다 (백엔드 명세 §4.1, 백엔드 명세 §6.2).

### 2.6 그룹

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/groups` | 그룹 목록 조회 (§2.6.1) | 필요 |
| POST | `/api/groups` | 그룹 생성 (§2.6.2) | 필요 |
| GET | `/api/groups/{id}` | 그룹 상세 조회 (§2.6.3) | 소유자·멤버 |
| PUT | `/api/groups/{id}` | 그룹 수정 (§2.6.4) | 소유자만 |
| DELETE | `/api/groups/{id}` | 그룹 삭제 (§2.6.5) | 소유자만 |
| DELETE | `/api/groups/{id}/members/{userId}` | 멤버 제외 (§2.6.6) | 소유자만 |
| DELETE | `/api/groups/{id}/members/me` | 그룹 탈퇴 (§2.6.7) | 멤버 본인 |

#### 2.6.1 그룹 목록 조회

`GET /api/groups`

- 내가 소유하거나 속한 그룹 목록이며, 각 항목에 `role: OWNER|MEMBER` 를 포함한다.
- 각 항목은 `memo` 도 포함한다. 그룹 카드에서 이름만으로 구분되지 않는 문제를 풀려고 넣은 값이라,
  상세로 들어가야만 보이면 목적을 채우지 못한다.

#### 2.6.2 그룹 생성

`POST /api/groups`

- 소유자를 멤버로 함께 입력한다.
- 요청 본문은 `{ "name": "가족", "memo": "설 연휴 사진 공유용" }` 이다. `memo` 는 생략 가능하며
  생략하면 `null` 로 저장된다.

#### 2.6.3 그룹 상세 조회

`GET /api/groups/{id}`

**응답 예시**
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
- 멤버가 아닌 사용자의 조회는 `403 FORBIDDEN` 이다. 존재하지 않는 그룹만 `404` 다 (백엔드 명세 §2.2.2).

#### 2.6.4 그룹 수정

`PUT /api/groups/{id}`

- 그룹 이름과 메모를 변경한다. 요청 본문은 생성과 같다 (§2.6.2).
- 두 값을 함께 덮어쓰므로 **`memo` 를 빼고 보내면 기존 메모가 지워진다** — 이름만 고치는 경우에도
  현재 메모를 함께 실어 보내야 한다.

#### 2.6.5 그룹 삭제

`DELETE /api/groups/{id}`

- 멤버·대기 초대·공유 관계도 함께 삭제한다.

#### 2.6.6 멤버 제외

`DELETE /api/groups/{id}/members/{userId}`

- 그 멤버가 이 그룹에 건 공유도 함께 해제한다.
- 소유자 제외 요청(`DELETE .../members/{소유자 id}`)은 `400 VALIDATION_ERROR` 다.
- 그룹에 속하지 않은 사용자를 제외하려 하면 `404 MEMBER_NOT_FOUND` 다.

#### 2.6.7 그룹 탈퇴

`DELETE /api/groups/{id}/members/me`

- 내가 이 그룹에 건 공유도 함께 해제한다.
- 소유자와 비멤버는 `403 FORBIDDEN` 이다. 존재하지 않는 그룹만 `404` 다 (백엔드 명세 §2.2.2).

### 2.7 초대

초대는 **보내는 쪽(그룹)** 과 **받는 쪽(사용자)** 두 경로로 나뉜다. 공개 토큰 URL은 존재하지
않으며 모든 엔드포인트가 로그인을 요구한다 (공통 명세 §2.1).

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/groups/{id}/invites` | 그룹의 대기 중인 초대 목록 (§2.7.1) | 소유자만 |
| POST | `/api/groups/{id}/invites` | 초대 보내기 (§2.7.2) | 소유자만 |
| DELETE | `/api/groups/{id}/invites/{inviteId}` | 초대 철회 (§2.7.3) | 소유자만 |
| GET | `/api/invites` | 받은 초대 목록 (§2.7.4) | 필요 |
| GET | `/api/invites/sent` | 보낸 초대 목록 (§2.7.5) | 필요 |
| GET | `/api/invites/history` | 초대 이력 (§2.7.6) | 당사자만 |
| POST | `/api/invites/{inviteId}/accept` | 초대 수락 (§2.7.7) | 받은 본인만 |
| POST | `/api/invites/{inviteId}/reject` | 초대 거절 (§2.7.8) | 받은 본인만 |

- 수락·거절·철회는 해당 행을 지우고 같은 트랜잭션에서 이력을 남긴다 (백엔드 명세 §3.1, 백엔드 명세 §3.2).
  성공 응답은 본문 없이 `204 No Content` 다.

#### 2.7.1 그룹의 대기 중인 초대 목록

`GET /api/groups/{id}/invites`

- **페이지네이션한다** (§3.2). **건수를 묶어 주는 상한이 없기 때문이다** — 대기 초대는 정원 판정이
  수락 시점이라 정원을 넘겨 보낼 수 있다. 정원(5명)이 곧 상한인 그룹 목록(§2.6.1)과는 다르다.
- 각 항목은 `invitee`·`createdAt` 만 있는 형태다. 어느 그룹인지는 화면에 이미 드러나 있다.

#### 2.7.2 초대 보내기

`POST /api/groups/{id}/invites`

**요청·응답 예시**
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
  가 이를 보장한다 (백엔드 명세 §3.1). 새로 만들어진 경우만 `201` 이다.
- 실패 응답: 가입자가 없으면 `404 USER_NOT_FOUND`, 이미 멤버면 `409 ALREADY_MEMBER` 다.
  **가입 여부를 은닉하지 않는 것은 의도된 선택이다** — 소유자가 오타를 알아차릴 유일한 수단이며,
  그 대가는 공통 명세 §7.2에 한계로 적혀 있다.

#### 2.7.3 초대 철회

`DELETE /api/groups/{id}/invites/{inviteId}`

- **그룹 규칙을 따른다** (백엔드 명세 §2.2.2). 그룹이 없으면 `404 GROUP_NOT_FOUND`, 소유자가
  아니면 `403 FORBIDDEN` 이다. 초대는 소유자 확인을 통과한 뒤에야 찾으므로, 소유자가 아닌
  사람에게는 `inviteId` 가 무엇이든 같은 `403` 이라 초대의 존재가 드러나지 않는다. 소유자가
  경로의 그룹에 없는 초대를 철회하려 하면 `404 INVITE_NOT_FOUND` 다.

#### 2.7.4 받은 초대 목록

`GET /api/invites`

- 내가 받은 대기 중인 초대 목록이다.
- **페이지네이션한다** (§3.2). 나를 초대할 수 있는 그룹 수에 제한이 없어 건수를 묶어 주는 상한이 없다.

**응답 예시**
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

#### 2.7.5 보낸 초대 목록

`GET /api/invites/sent`

- 내가 보낸 대기 중인 초대 목록이며, 그룹을 가로지른다.
- **조건은 `invited_by = 나` 다.** 그룹을 조인해 소유자를 보는 것과 결과가 같지만(소유자는 바뀌지
  않는다) 조인 없이 인덱스 하나로 끝나고, 남이 보낸 초대가 섞일 수 없어 인가가 조건 자체로
  보장된다. `group_invite(invited_by)` 인덱스를 추가한다 — 현재 인덱스는 `invitee_id` 와
  `uk_group_invite`(선두 `group_id`) 뿐이라 이 조회가 풀스캔이 된다.
- **각 항목은 `{ id, group: { id, name }, invitee, createdAt }` 다.** 그룹을 가로지르는 목록이라
  그룹명이 함께 필요하며, 그룹의 대기 중인 초대 목록(§2.7.1)과 응답을 나누어 둔다.

#### 2.7.6 초대 이력

`GET /api/invites/history?role=received|sent`

- 끝난 초대의 이력이다.
- **`role` 로 관점을 고른다.** `received` 면 `invitee_id = 나`, `sent` 면 `invited_by = 나` 이며,
  생략하면 `received` 다. 정렬은 `resolvedAt DESC` 이고
  `invite_history(invitee_id, resolved_at DESC)`·`(invited_by, resolved_at DESC)` 인덱스를 둔다.
- **페이지네이션한다** (§3.2). 대기 초대와 달리 상한이 없을 뿐 아니라 지우지 않으므로
  계정이 오래될수록 길어진다.
- **이력도 당사자만 본다.** `role` 과 무관하게 남의 이력은 조회 경로가 없으며, 조건이 곧
  본인 필터다 (공통 명세 §2.6).

**응답 예시 (`role=sent`)**
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

#### 2.7.7 초대 수락

`POST /api/invites/{inviteId}/accept`

- 수락하면 그룹 멤버로 가입한다.
- **당사자가 아닌 초대는 `404 INVITE_NOT_FOUND` 다.** 보낸 소유자와 받은 사람 외에는 그 초대의
  존재가 드러나지 않아야 하며, 없는 `inviteId` 와 응답이 같아야 한다 (백엔드 명세 §2.2.2).
- 정원(5명)이 차 있으면 `409 GROUP_MEMBER_LIMIT_EXCEEDED` 이고 **초대 행은 남는다.**
  자리가 난 뒤 같은 초대로 다시 수락할 수 있어야 하기 때문이다 (백엔드 명세 §3.1).
- 수락 처리는 정원 검사와 `GroupMember` 입력을 **한 트랜잭션에서, 그룹 행을 잠근 뒤** 수행한다.
  정원을 강제하는 DB 제약이 없어 애플리케이션 검사가 유일한 관문이므로, 검사와 입력 사이에 다른
  수락이 끼어들면 6명짜리 그룹이 만들어진다.
- 그룹이 삭제되면 그 그룹의 초대도 함께 사라지므로(백엔드 명세 §3.2), 이미 받은 목록에 있던 초대의 수락이
  `404 INVITE_NOT_FOUND` 가 될 수 있다. 정상 동작이다.

#### 2.7.8 초대 거절

`POST /api/invites/{inviteId}/reject`

- 당사자가 아닌 초대에 대한 응답은 수락과 같다 (§2.7.7).

### 2.8 태그

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api/tags` | 태그 자동완성 (§2.8.1) | 선택 |

#### 2.8.1 태그 자동완성

`GET /api/tags?keyword=`

- 자동완성 후보는 **요청자가 볼 수 있는 기록에 쓰인 태그**로 제한한다. 전체 태그를 그대로
  내려주면 비공개 기록에만 쓰인 태그가 노출되기 때문이다.
- 볼 수 있는지는 백엔드 명세 §2.2 대로 **소속 여행을 조인해서** 판정한다. 태그 조회라고 해서 판정을
  건너뛰면 그대로 정보 유출이 된다.
- 결과는 이름순 **상위 20건**까지다. 자동완성 후보라 그 이상은 쓰이지 않는다.
- `keyword` 의 `%`·`_` 는 와일드카드가 아니라 글자 그대로 찾는다. 여행·기록 목록의 `keyword` 도 같다.

## 3. 공통 규칙

기본 경로는 `/api` 이고 응답은 JSON 이다.

### 3.1 조회 범위 (`scope`)

**여행·기록 목록**은 **어떤 범위를 보는지**를 항상 명시한다. 세 범위는 성격이 달라 한 목록에 섞지
않으며, `scope` 는 그 두 목록의 **필수 파라미터**다. 초대 목록(§2.7)은 당사자 것만 보이므로 고를
범위가 없어 받지 않는다.

| `scope` | 여행 목록에서 | 기록 목록에서 | 비로그인 |
|---|---|---|:---:|
| `mine` | `owner` 가 요청자인 여행 전부 (공개 범위 무관) | 그 여행들의 하위 기록 | `401` |
| `shared` | 요청자가 멤버인 그룹으로 공유된 **타인의** 여행 | 그 여행들의 하위 기록 | `401` |
| `public` | `visibility=PUBLIC` 인 여행 전부 (본인 것 포함) | 그 여행들의 하위 기록 | ✅ |

- `scope` 는 **권한을 넓히는 수단이 아니다.** 어떤 값이든 서버는 백엔드 명세 §2.2 판정을 통과한 것만
  반환한다. 이미 볼 수 있는 것 중에서 고르는 수단일 뿐이다.
- `scope=shared` 는 본인 것을 제외한다. 본인 것은 `mine` 에서 이미 전부 보이므로 중복을 피한다.
- **기록 목록의 범위 판정도 소속 여행으로 한다** (백엔드 명세 §2.2). 기록 자체에는 판정 근거가 없다.

### 3.2 페이지네이션

모든 목록 응답은 `content`, `page`(0-base), `size`, `totalElements`, `totalPages` 를 포함한다.
**요청이 받는 것은 `page` 하나뿐이고 페이지 크기는 서버가 정한다.** 크기를 클라이언트가 고르게
두면 한 번에 전부 받아 가는 요청을 막을 상한이 매번 필요해지고, 그 상한 자체가 또 하나의 값
규칙이 된다.

| 목록 | 페이지 크기 |
|---|---|
| 여행 목록 (`/api/trips`) | 10 |
| 기록 목록 (`/api/records`) | 10 |
| 장소 검색 (`/api/places/search`) | 5 |
| 초대 목록 (`/api/invites`, `/api/groups/{id}/invites`) | 10 |
| 로그인 이력 (`/api/auth/me/login-history`) | 10 |

- **요청에 `size` 를 담아도 무시한다.** 응답의 `size` 는 서버가 적용한 크기를 알려주는 값이다.
- `page` 가 음수면 `0` 으로 보고, 범위를 넘는 `page` 는 오류가 아니라 **빈 `content`** 다.
- **장소 검색만 5건인 것은 원본 API가 호출당 5건까지만 주기 때문이다** (공통 명세 §4.2 1항).
  페이지 크기를 그보다 키우면 첫 페이지조차 채우지 못한 채 다음 페이지가 늘 비게 된다 (§2.4.1).
- 여행 상세의 하위 기록도 같은 스키마를 쓴다 (`tripId` 로 한정, §2.3.1).

### 3.3 오류 응답 형식

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
| 존재하지 않거나 **열람 권한이 없는** 여행, 기록 등록·이동의 대상이 내 여행이 아님 (백엔드 명세 §2.2.2) | 404 | `TRIP_NOT_FOUND` |
| 존재하지 않거나 **열람 권한이 없는** 기록 | 404 | `RECORD_NOT_FOUND` |
| 존재하지 않는 그룹, 여행 공유 요청에 담긴 접근 불가 그룹 id | 404 | `GROUP_NOT_FOUND` |
| 제외 대상이 그룹 멤버가 아님 | 404 | `MEMBER_NOT_FOUND` |
| 존재하지 않는 사진 | 404 | `PHOTO_NOT_FOUND` |
| 존재하지 않거나 **당사자가 아닌** 초대 | 404 | `INVITE_NOT_FOUND` |
| 초대 대상 이메일의 가입자가 없음 | 404 | `USER_NOT_FOUND` |
| 매핑되지 않은 주소, 존재하지 않는 정적 파일 | 404 | `NOT_FOUND` |
| 지원하지 않는 HTTP 메서드 | 405 | `METHOD_NOT_ALLOWED` |
| 지원하지 않는 `Content-Type` | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 그 밖에 상태만 알려진 요청 실패 (협상 불가 `Accept` 등). 상태는 원래 값을 유지 | 해당 4xx | `BAD_REQUEST` |
| 그룹 정원(5명) 초과 | 409 | `GROUP_MEMBER_LIMIT_EXCEEDED` |
| 이미 멤버인 사용자를 초대 | 409 | `ALREADY_MEMBER` |
| 그 밖의 DB 제약 위반 (동시 요청 경합 등) | 409 | `CONFLICT` |
| 지역 검색 API 호출 실패/한도 초과 | 502 | `PLACE_SEARCH_UNAVAILABLE` |
| 그 밖의 처리되지 않은 예외 | 500 | `INTERNAL_ERROR` |

- `500` 과 `409` 의 `message` 는 고정 문구로 내려간다. 예외 메시지에 테이블·제약 이름이나 내부
  구조가 드러날 수 있어 응답에 싣지 않고 서버 로그로만 남긴다.
- `400` 의 `message` 는 어느 필드가 왜 틀렸는지 담는다 (예: `rating: 평점은 0.5점 단위로만 입력할 수 있습니다.`).
- **`404 RECORD_NOT_FOUND`·`TRIP_NOT_FOUND` 의 `message` 는 존재하지 않는 경우와 권한 없는 경우가
  동일해야 한다.** 문구가 다르면 그 차이만으로 존재가 드러난다.
- **소속 여행을 볼 수 없어 가려지는 기록은 `RECORD_NOT_FOUND` 로 응답한다** (백엔드 명세 §2.2).
  `TRIP_NOT_FOUND` 를 쓰면 기록의 존재가 드러난다.
