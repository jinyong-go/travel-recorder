# frontend/AGENTS.md

프론트엔드 모듈 규칙이다. 공통 규칙은 [루트 AGENTS.md](../AGENTS.md), 요구사항은 [`frontend-specification.md`](../docs/frontend-specification.md),
API 시그니처는 [`backend-specification.md`](../docs/backend-specification.md)를 따른다.

## 명령

```bash
npm run dev       # 개발 서버 (5173 — compose 의 web 과 겹치므로 동시에 띄우지 않는다)
npm run lint      # oxlint
npm run build     # 프로덕션 빌드
```

## 스택·스타일

- React 19 / Vite / react-router, **순수 JavaScript**. TypeScript(`.ts`·`.tsx`·타입 애너테이션)를 도입하지 않는다.
  타입 설명이 필요하면 JSDoc 을 쓴다.
- 세미콜론 없음, 작은따옴표. 포매터가 없으므로 주변 코드에 맞춘다.

## 파일 배치

- 라우트 화면은 `pages/XxxPage.jsx`. 재사용이 두 번째 생기면 `components/` 로 옮긴다. 처음부터 빼지 않는다.
  단 화면과 독립적인 부품(모달, 입력 컨트롤, 라우트 가드, 지도 뷰)은 처음부터 `components/` 에 둔다.
- 훅은 두 화면 이상이 쓸 때 `hooks/` 로 올린다. 외부 SDK 로더(`useNaverMapsSdk`)는 예외로 처음부터 둔다.
- 부모와 자식 모달이 같은 훅 상태를 봐야 하면 부모가 결과를 prop 으로 넘긴다. 자식이 다시 부르면 상태가 갈라진다.

## export

- 컴포넌트·훅: default export, 파일 하나에 하나, 파일명 = 이름 (`VisibilityBadge.jsx`, `usePagedList.js`).
- 유틸·설정·컨텍스트·API 모듈: named export.
- 예외: 아이콘은 `components/icons.jsx` 에 named export 로 모은다. 그 화면만 쓰는 안쪽 컴포넌트
  (`TripDetailPage` 의 `TripDetailView`·`TripVisibilitySection`)는 같은 파일에 둔다.

## CSS

- 일반 CSS 만 쓴다. CSS Modules·Tailwind·CSS-in-JS 를 도입하지 않는다.
- `Foo.jsx` 의 스타일은 옆의 `Foo.css` 에 두고 컴포넌트가 import 한다.
- 범용 UI(헤더·모달 틀·버튼·오류 문구·빈 상태·페이지네이션)는 `App.css`, 두 화면 이상이 쓰는 화면 틀은 `src/styles/`.
- import 순서는 공유 CSS 먼저, 자기 CSS 마지막이다. 다른 화면의 CSS 를 가져다 쓰지 않는다.
  예외: 별점 표시는 입력의 `StarRatingInput.css` 를 함께 쓴다 (쓰는 곳이 둘뿐이다).
- 클래스명은 kebab-case, 컴포넌트 이름을 접두사로 (`visibility-badge-icon`). `styles/` 의 공유 틀은 틀 이름을
  접두사로 쓴다 (`detail-*`, `invite-*`).
- 색상은 CSS 변수로만 쓴다 — 라이트/다크가 같은 변수를 공유한다. 테마와 무관한 고정 색(브랜드 색)도
  변수로 정의하고 다크 값을 따로 두지 않는다.

## 상태

- 서버 데이터는 쓰는 화면이 직접 조회한다. 전역 컨텍스트는 `AuthContext` 하나이며 서버 데이터를 캐시하지
  않는다. 여러 화면이 같은 조회를 하면 훅으로 모은다 (`useTrip`).
- **조회는 `useResource` 또는 `usePagedList` 로 한다.** 화면에서 `useEffect` + 취소 플래그를 새로 짜지 않는다.
  - `useResource`: 단건, 전체 목록, 번호 페이지네이션(페이지마다 새로 읽기)
  - `usePagedList` + `PagedListBody`: "더 보기"로 누적하는 목록
- 같은 화면의 두 컴포넌트가 같은 값을 쓰면 조회한 쪽이 prop 으로 넘긴다.
- 낙관적 갱신을 하지 않는다. 서버만 아는 규칙(정원 등)이 있어 쓰기 성공 후 다시 읽는다.
- 개발 모드의 이중 조회는 StrictMode 때문이며 유지한다.
- 전역 상태 라이브러리를 넣지 않는다.
- 사용자 선택(테마·기준 위치·지도 방식)은 로컬 저장소 + 전용 훅으로 둔다.
- 목록의 범위·필터·정렬·페이지는 쿼리 파라미터로 유지한다.

## 공개 범위 UI — 가장 조심할 자리

`frontend-specification.md` 의 "공개 범위 관련 UI 원칙" 절을 따른다.

- **공개 범위 기본 선택은 언제나 "나만 보기"다.**
- 공개 범위는 여행에만 있다. 기록 화면에 범위를 바꾸는 수단을 두지 않고, 표시되는 범위가 소속 여행의 것임이 드러나야 한다.
- 세 상태를 색상만으로 구분하지 않는다. 아이콘과 텍스트를 함께 쓴다.
- 범위를 넓히는 변경은 결과를 문장으로 확인시키고 영향받는 기록 수를 함께 말한다.
- 사진 URL 은 공개 범위와 무관하게 접근 가능하다. "비공개 사진은 절대 노출되지 않는다"고 오해할 문구를 쓰지 않는다.
- 공개 범위 판정은 서버가 한다. 화면은 `404` 를 "없음"으로 그릴 뿐 판정식을 갖지 않는다.

## API 연동

- 모든 요청은 `credentials: 'include'`, 쓰기 요청에는 `X-XSRF-TOKEN` 헤더를 붙인다 (`api/client.js`).
- 오류 분기는 HTTP 상태가 아니라 응답의 `code` 로 한다. 서버 `message` 를 화면에 그대로 띄우지 않고
  `errorMessage(err, 코드별 표, 기본 문구)` 로 고른다. 예외: `useResource` 는 404 를 상태로 판정해 "없음(MISSING)"
  으로 바꾼다 — 여러 `*_NOT_FOUND` 를 한 화면 상태로 모으는 자리다.
- 네이버 검색·OAuth 키를 브라우저에 두지 않는다. 장소 검색은 백엔드를 경유한다. 브라우저 키는 지도 Client ID 하나뿐이다.
- 백엔드와 같아야 하는 값(사진 제한, 입력 길이 등)은 `config/` 에 둔다. 배포마다 달라질 수 있는 값만
  환경변수로 받는다. 컴포넌트에 숫자를 하드코딩하지 않는다.
- 서버가 주는 파일 URL(`/api/files/...`)은 `fileUrl()` 로 감싸 쓴다.

## 접근성·반응형

- 모달은 `role="dialog"`·`aria-modal`, 폼 입력에는 라벨, 공개 범위 라디오는 `fieldset`·`legend`.
- 계산되어 바뀌는 값(여행 기간의 박·일 수 등)은 `aria-live="polite"`.
- 모바일 360px 부터 데스크톱까지 대응한다.
