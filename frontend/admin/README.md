# 여행 지도 (travel-recorder) — 관리자 웹

운영자가 이용 현황을 보는 관리자 웹. **지금은 목업 데이터로 동작하는 초안**이며 백엔드를 부르지 않는다.

화면과 동작은 [admin-specification.md](../../docs/admin-specification.md), 작업 규칙은 [AGENTS.md](../AGENTS.md) 참고.

## 기술 스택

사용자 웹(`../user`)과 같음 — React 19, react-router-dom 7, Vite 8, Oxlint, 순수 CSS. 코드는 공유하지 않는 독립 패키지.

## 시작하기

```bash
cd frontend/admin
npm install
npm run dev       # http://localhost:5174
```

환경 변수는 없음. Docker로 띄우려면 저장소 루트에서 `docker compose up admin` (같은 5174 포트라 개발 서버와 동시에 띄우지 않음).

## 사용 가능한 스크립트

| 명령 | 설명 |
|---|---|
| `npm run dev` | 개발 서버 실행 (포트 5174, HMR 지원) |
| `npm run build` | 프로덕션 빌드 생성 → `dist/` |
| `npm run preview` | 빌드 결과물 미리보기 (포트 5174) |
| `npm run lint` | Oxlint 정적 분석 실행 |

## 디렉터리 구조

```
frontend/admin/
├─ public/favicon.svg
├─ src/
│  ├─ main.jsx / App.jsx        # 엔트리, 라우트 정의
│  ├─ index.css                 # 전역 스타일, 색상 변수 (라이트·다크는 시스템 설정을 따름)
│  ├─ api/                      # 화면이 부르는 조회 함수. 지금은 mock/ 데이터를 돌려줌
│  ├─ mock/data.js              # 고정 목업 데이터 (로그인 통계 14일, 사용자 47명)
│  ├─ hooks/                    # useResource·usePagedList (사용자 웹에서 복사)
│  ├─ components/AdminLayout.*  # 왼쪽 메뉴 + 상단 바 + "목업 데이터" 배지
│  └─ pages/                    # 대시보드(/), 사용자(/users), 없는 페이지
├─ Dockerfile / nginx.conf      # Node 빌드 → nginx 서빙, SPA 폴백
└─ vite.config.js               # 개발·미리보기 포트 5174
```

## 백엔드 연동

관리자 역할과 관리자 API 가 생기면 `src/api/*.js` 의 함수 내부를 실제 요청으로 바꾼다. 화면은 그대로 둔다.
요청 경로·인증 방식 등 정할 것은 [admin-specification.md §3](../../docs/admin-specification.md#3-백엔드-연동-전에-정할-것) 참고.
local 프로파일 CORS 에는 `http://localhost:5174` 가 이미 허용되어 있음.
