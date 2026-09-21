# 여행 지도 (travel-recorder) — Frontend

다녀온 여행을 기록으로 남기고 원하는 상대에게만 공유하는 웹 애플리케이션의 프론트엔드입니다.
네이버 로그인으로 인증한 사용자가 **여행**(기간·인원·예산)을 만들고 그 하위에 방문한 장소를
기록으로 남깁니다. 공개 범위(나만 보기 / 그룹 공유 / 전체 공개)는 **여행 단위로** 정하며 하위
기록이 전부 함께 따라갑니다. 목록은 내 여행·공유받은 여행·둘러보기로 나뉩니다.

상세 요구사항은 [SPECIFICATION.md](./SPECIFICATION.md)를 참고하세요.

## 기술 스택

| 구분 | 사용 기술 | 버전 |
|---|---|---|
| 언어 | JavaScript (ESM, JSX) | — |
| UI 라이브러리 | React | ^19.2 |
| 라우팅 | react-router-dom | ^7.18 |
| 빌드 도구 | Vite (`@vitejs/plugin-react`) | ^8.3 |
| 린터 | Oxlint | ^1.81 |
| 스타일 | 순수 CSS (CSS 변수 기반 테마) | — |
| 상태 관리 | React Context + Hooks (`RecordsContext`) | — |

- 별도의 CSS 프레임워크나 상태 관리 라이브러리(Redux 등)는 사용하지 않습니다.
- 요구 Node.js 버전: **20 이상** (Vite 8 기준, 개발 환경 검증 버전 Node 24 / npm 12)

## 디렉터리 구조

```
frontend/
├─ public/
│  ├─ favicon.svg              # 서비스 파비콘
│  └─ icons.svg                # SVG 스프라이트
├─ src/
│  ├─ main.jsx                 # 엔트리 (BrowserRouter 마운트)
│  ├─ App.jsx                  # 라우트 정의 + RecordsProvider
│  ├─ index.css / App.css      # 전역 스타일, 테마 변수
│  ├─ pages/
│  │  ├─ LandingPage.jsx       # 인덱스/랜딩 (/)
│  │  ├─ LoginPage.jsx         # 로그인 (/login, 네이버 OAuth 진입점)
│  │  ├─ TripListPage.jsx      # 여행 목록 (/trips, 범위 탭·정렬·페이지네이션)
│  │  ├─ TripRegisterPage.jsx  # 여행 만들기 (/trips/new)
│  │  ├─ TripDetailPage.jsx    # 여행 상세 (/trips/:tripId, 하위 기록·공개 범위·정보 수정·삭제)
│  │  ├─ RecordDetailPage.jsx  # 기록 상세·수정 (/records/:recordId)
│  │  ├─ RegisterRecordPage.jsx # 기록 등록 (/records/register, 소속 여행 선택)
│  │  ├─ GroupsPage.jsx        # 공유 그룹 목록·생성, 받은 초대 (/groups)
│  │  ├─ GroupDetailPage.jsx   # 그룹 상세 — 멤버·이메일 초대·삭제 (/groups/:groupId)
│  │  └─ NotFoundPage.jsx      # 404 (그 외 모든 경로)
│  │                           # /records 로 들어온 요청은 /trips 로 보낸다
│  ├─ components/
│  │  ├─ PlaceSearchModal.jsx  # 장소 검색 모달 (10건 페이징, 거리순 정렬)
│  │  ├─ PlaceMapModal.jsx     # 지도 모달 (목록·상세 공용)
│  │  ├─ NaverMapView.jsx      # 네이버 지도 SDK v3 렌더링 + 마커
│  │  ├─ StarRatingInput.jsx   # 평점 입력 (0.5점 단위)
│  │  ├─ StarRatingDisplay.jsx # 평점 표시 (읽기 전용)
│  │  ├─ TripForm.jsx          # 여행 입력 폼 (만들기·정보 수정 공용, 공개 범위는 제외)
│  │  ├─ VisibilitySelect.jsx  # 공개 범위 선택 + 공유 그룹 다중 선택 (여행 전용)
│  │  ├─ VisibilityBadge.jsx   # 공개 범위 배지 (소유자에게만 노출, 아이콘+텍스트)
│  │  ├─ SettingsMenu.jsx      # 지도 표시 방식 설정
│  │  ├─ ThemeSelector.jsx     # 라이트/다크 테마 토글
│  │  └─ icons.jsx             # 아이콘 컴포넌트
│  ├─ context/RecordsContext.jsx       # 여행·기록·그룹·초대 전역 상태 + 공개 범위 판정
│  ├─ hooks/
│  │  ├─ useReferenceLocation.js       # 기준 위치(Geolocation/수동/기본값) 훅
│  │  ├─ useTheme.js                   # 라이트/다크 테마 상태 (테마 토글이 있는 화면 공용)
│  │  ├─ useNaverMapsSdk.js            # 네이버 지도 SDK 스크립트 로드 상태
│  │  └─ useMapMode.js                 # 지도 표시 방식 상태 (지도 버튼이 있는 화면 공용)
│  ├─ config/
│  │  ├─ api.js                        # 백엔드 오리진·네이버 로그인 진입 URL
│  │  ├─ mapSettings.js                # 지도 표시 방식·지도 URL 생성
│  │  ├─ uploadLimits.js               # 사진 용량·형식 제한 및 검증
│  │  └─ referenceLocation.js          # 기준 위치 저장/조회, 기본값(서울역)
│  ├─ data/                            # 백엔드 연동 전 목업 데이터 (trips·records·groups)
│  ├─ theme/themes.js                  # 라이트/다크 테마 정의 및 로컬 저장
│  └─ utils/geo.js                     # 하버사인 거리 계산
├─ index.html
├─ vite.config.js
├─ .oxlintrc.json
└─ .env.example
```

## 시작하기

### 1. 의존성 설치

```bash
cd frontend
npm install
```

### 2. 환경 변수 설정

`.env.example`을 복사해 `.env` 파일을 만들고 필요한 값을 채웁니다. (`.env`는 커밋 대상이 아닙니다.)

```bash
cp .env.example .env
```

| 변수 | 필수 | 설명 |
|---|---|---|
| `VITE_API_BASE_URL` | 선택 | 백엔드 오리진. 기본 `http://localhost:8080`. 네이버 로그인 진입 URL(`/oauth2/authorization/naver`)도 이 값을 기준으로 만듭니다. |
| `VITE_NAVER_MAP_CLIENT_ID` | 선택 | 네이버 지도 SDK Client ID. 지도를 페이지 내에 표시할 때 필요합니다. 값이 없으면 설정 메뉴에서 "새 창으로 열기"(네이버 지도 검색) 방식만 사용할 수 있습니다. |
| `VITE_MAX_PHOTO_SIZE_MB` | 선택 | 사진 1장당 최대 용량(MB). 기본 `5`. |
| `VITE_MAX_PHOTO_TOTAL_MB` | 선택 | 한 번에 첨부할 수 있는 전체 용량(MB). 기본 `30`. |

> 업로드 제한은 백엔드 설정과 같은 값을 유지해야 합니다. 백엔드는 `MAX_PHOTO_SIZE`(기본 `5MB`),
> `MAX_PHOTO_REQUEST_SIZE`(기본 `30MB`), `ALLOWED_PHOTO_CONTENT_TYPES` 환경 변수로 조정합니다.
> 프론트엔드 검증은 사용자 편의용이며, 최종 검증은 백엔드가 수행합니다.

> Vite는 `VITE_` 접두사가 붙은 변수만 클라이언트 번들에 노출합니다.
> 노출되면 안 되는 키(네이버 검색 API의 Client Secret 등)는 프론트엔드가 아닌 백엔드 환경 변수로 관리하세요.

### 3. 개발 서버 실행

```bash
npm run dev
```

기본 주소는 http://localhost:5173 입니다. HMR(핫 리로드)이 적용됩니다.

## 사용 가능한 스크립트

| 명령 | 설명 |
|---|---|
| `npm run dev` | 개발 서버 실행 (기본 포트 5173, HMR 지원) |
| `npm run build` | 프로덕션 빌드 생성 → `dist/` |
| `npm run preview` | 빌드 결과물(`dist/`)을 로컬 정적 서버로 미리보기 |
| `npm run lint` | Oxlint 정적 분석 실행 |

## 빌드 및 배포

```bash
npm run build     # dist/ 에 정적 파일 생성
npm run preview   # 빌드 결과 확인
```

`dist/`는 정적 파일 묶음이므로 임의의 정적 호스팅(Nginx, S3+CloudFront 등)에 그대로 배포할 수 있습니다.
SPA 라우팅(`/records/register` 등)을 사용하므로, 서버에서 **알 수 없는 경로를 `index.html`로 폴백**하도록 설정해야 새로고침 시 404가 발생하지 않습니다.

## 백엔드 연동

- 백엔드는 같은 저장소의 `backend/` (Spring Boot + Kotlin)이며 기본 포트는 **8080**입니다.
- API 기본 경로는 `/api` 이하입니다. (`/api/auth`, `/api/trips`, `/api/records`, `/api/records/{recordId}/photos`, `/api/groups`, `/api/invites`, `/api/places/search`, `/api/tags`)
- 백엔드는 CORS 허용 오리진으로 `http://localhost:5173`을 설정해 두었으므로, 개발 시 프론트엔드를 기본 포트로 실행하면 별도 프록시 설정 없이 호출할 수 있습니다.
- 현재 프론트엔드는 **아직 API를 호출하지 않고 `src/data/`의 목업 데이터로 화면을 구성**합니다. 실제 연동 시 API 응답 스키마에 맞춘 매핑 레이어를 추가합니다.

## 현재 구현 상태

구현 완료
- 랜딩 페이지 (`/`), 404 페이지 (정의되지 않은 모든 경로)
- 여행 목록 화면 (`/trips`) — 범위 탭(내 여행/공유받은 여행/둘러보기), 정렬(최신순/시작일순), 페이지네이션. 범위/정렬/페이지는 쿼리 파라미터(`?scope=&sort=&page=`)로 유지되어 상세에서 돌아와도 보존됩니다. `/records` 로 들어온 요청은 `/trips` 로 보냅니다
- 여행 만들기 (`/trips/new`) — 이름·기간·인원·예산·설명. 공개 범위 기본값은 나만 보기
- 여행 상세 (`/trips/:tripId`) — 하위 기록 카드 목록, 공개 범위 변경(소유자 전용, 접을 수 있음), 정보 수정(확인 모달), 삭제 확인 모달(함께 지워지는 기록 건수를 문장으로 명시)
- 기록 상세·수정 (`/records/:recordId`) — 사진 갤러리, 평점·메모, 소속 여행 링크와 읽기 전용 범위 배지, 수정 폼(장소·카테고리·평점·메모·사진), 삭제 확인 모달
- 기록 등록 (`/records/register`) — 소속 여행 선택, 장소 검색 모달, 평점 입력, 사진 첨부
- 공유 그룹 (`/groups`, `/groups/:groupId`) — 그룹 생성, 받은 초대 수락·거절, 멤버 목록·제외·탈퇴, 이메일 초대 발송과 대기 초대 철회, 그룹 삭제
- 로그인 페이지 (`/login`) — 네이버 OAuth 진입점(백엔드 `/oauth2/authorization/naver`로 전체 페이지 이동)
- 지도 연동 — 네이버 지도. 설정 메뉴에서 "새 창으로 열기"(지도 검색)와 "페이지 내 지도 보기"(SDK v3 + 마커, Client ID 필요) 중 선택
- 라이트/다크 테마 토글(시스템 설정 기본값, 선택 시 로컬 저장), 기준 위치(Geolocation → 수동 지정 → 서울역) 처리

미구현 / 예정 (상세는 SPECIFICATION.md 10장)
- 네이버 로그인 이후 처리 — 세션 유지, 로그인 상태 UI(프로필·로그아웃), 로그인 사용자 판별. 현재는 목업 사용자(`CURRENT_USER`) 기준으로 소유자 여부를 판단합니다
- 백엔드 API 연동 (현재 목업 데이터 사용). 사진 업로드·삭제도 목업 단계이며 연동 지점은 코드에 TODO 로 표시되어 있습니다
- 여행 대표 사진 지정, 기록의 소속 여행 변경(다른 여행으로 옮기기), 하위 기록 목록 페이지네이션
- 태그 입력·필터 UI (백엔드는 지원하지만 화면이 없어 등록 요청이 항상 빈 태그 목록으로 나갑니다)
