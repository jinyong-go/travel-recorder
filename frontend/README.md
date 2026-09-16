# 여행 지도 (travel-recorder) — Frontend

여행 장소를 기록하고 공유하는 웹 애플리케이션의 프론트엔드입니다.
네이버 로그인으로 인증한 사용자가 다녀온 여행지를 등록하고, 카테고리·정렬 조건으로
목록을 조회하며 지도로 위치를 확인할 수 있습니다.

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
| 상태 관리 | React Context + Hooks (`PlacesContext`) | — |

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
│  ├─ App.jsx                  # 라우트 정의 + PlacesProvider
│  ├─ index.css / App.css      # 전역 스타일, 테마 변수
│  ├─ pages/
│  │  ├─ LandingPage.jsx       # 인덱스/랜딩 (/)
│  │  ├─ LoginPage.jsx         # 로그인 (/login, 네이버 OAuth 진입점)
│  │  ├─ MainPage.jsx          # 메인 목록 화면 (/places, 카테고리 탭·정렬·페이지네이션)
│  │  ├─ PlaceDetailPage.jsx   # 여행지 상세 (/places/:placeId, 사진·별점 분포·리뷰)
│  │  ├─ RegisterPlacePage.jsx # 여행지 등록 페이지 (/places/register)
│  │  └─ NotFoundPage.jsx      # 404 (그 외 모든 경로)
│  ├─ components/
│  │  ├─ PlaceSearchModal.jsx  # 장소 검색 모달 (10건 페이징, 거리순 정렬)
│  │  ├─ PlaceMapModal.jsx     # 지도 모달 (목록·상세 공용)
│  │  ├─ NaverMapView.jsx      # 네이버 지도 SDK v3 렌더링 + 마커
│  │  ├─ StarRatingInput.jsx   # 별점 입력 (0.5점 단위)
│  │  ├─ StarRatingDisplay.jsx # 별점 표시 (읽기 전용)
│  │  ├─ SettingsMenu.jsx      # 지도 표시 방식 설정
│  │  ├─ ThemeSelector.jsx     # 라이트/다크 테마 토글
│  │  └─ icons.jsx             # 아이콘 컴포넌트
│  ├─ context/PlacesContext.jsx        # 여행지 목록 전역 상태
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
│  ├─ data/                            # 백엔드 연동 전 목업 데이터
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
SPA 라우팅(`/places/register` 등)을 사용하므로, 서버에서 **알 수 없는 경로를 `index.html`로 폴백**하도록 설정해야 새로고침 시 404가 발생하지 않습니다.

## 백엔드 연동

- 백엔드는 같은 저장소의 `backend/` (Spring Boot + Kotlin)이며 기본 포트는 **8080**입니다.
- API 기본 경로는 `/api` 이하입니다. (`/api/auth`, `/api/places`, `/api/places/{placeId}/photos`, `/api/places/{placeId}/reviews`, `/api/tags`)
- 백엔드는 CORS 허용 오리진으로 `http://localhost:5173`을 설정해 두었으므로, 개발 시 프론트엔드를 기본 포트로 실행하면 별도 프록시 설정 없이 호출할 수 있습니다.
- 현재 프론트엔드는 **아직 API를 호출하지 않고 `src/data/`의 목업 데이터로 화면을 구성**합니다. 실제 연동 시 API 응답 스키마에 맞춘 매핑 레이어를 추가합니다.

## 현재 구현 상태

구현 완료
- 랜딩 페이지 (`/`), 404 페이지 (정의되지 않은 모든 경로)
- 메인 목록 화면 (`/places`) — 카테고리 탭, 정렬, 페이지네이션, 지도 모달, 등록 FAB. 필터/정렬/페이지는 쿼리 파라미터(`?category=&sort=&page=`)로 유지
- 여행지 상세 화면 (`/places/:placeId`) — 사진 갤러리, 평균 별점·별점 분포, 리뷰 작성/수정/삭제 (별점+코멘트 한 폼)
- 여행지 등록 페이지 (`/places/register`) 및 장소 검색 모달, 별점 입력
- 로그인 페이지 (`/login`) — 네이버 OAuth 진입점(백엔드 `/oauth2/authorization/naver`로 전체 페이지 이동)
- 지도 연동 — 네이버 지도. 설정 메뉴에서 "새 창으로 열기"(지도 검색)와 "페이지 내 지도 보기"(SDK v3 + 마커, Client ID 필요) 중 선택
- 라이트/다크 테마 토글(시스템 설정 기본값, 선택 시 로컬 저장), 기준 위치(Geolocation → 수동 지정 → 서울역) 처리

미구현 / 예정 (상세는 SPECIFICATION.md 9장)
- 네이버 로그인 이후 처리 — 세션 유지, 로그인 상태 UI(프로필·로그아웃), 로그인 사용자 판별. 상세 화면의 "내 리뷰"는 현재 목업 사용자 기준입니다
- 백엔드 API 연동 (현재 목업 데이터 사용)
