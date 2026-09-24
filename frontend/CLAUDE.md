# frontend/CLAUDE.md

프론트엔드 모듈에서 작업할 때의 규칙이다. 승인 절차·명세 작업·코드 원칙·주석 규칙은
[루트 CLAUDE.md](../CLAUDE.md)에 있고 **여기서 반복하지 않는다.**

요구사항은 [`SPECIFICATION.md`](./SPECIFICATION.md)를 따른다. API 시그니처는
[`backend/SPECIFICATION.md`](../backend/SPECIFICATION.md)를 본다.

## 명령

```bash
npm run dev       # 개발 서버 (5173)
npm run lint      # oxlint
npm run build     # 프로덕션 빌드
```

## 스택

React 19 / Vite / react-router / **순수 JavaScript**

> **TypeScript가 아니다.** 타입 애너테이션·`interface`·`.ts`/`.tsx` 를 도입하지 않는다.
> 타입이 필요한 자리는 JSDoc 주석으로 설명한다. 도입하려면 별도 논의가 필요하다.

## 디렉터리

```
src/
  pages/       라우트 단위 화면 (XxxPage.jsx)
  components/  재사용 UI (PascalCase.jsx)
  hooks/       공용 훅 (useXxx.js)
  context/     전역 상태 (XxxContext.jsx)
  config/      설정·환경변수 (camelCase.js)
  utils/       순수 함수 (camelCase.js)
  data/        목업 데이터 — API 연동 시 걷어낼 자리
```

- **라우트가 생기면 `pages/`, 재사용이 두 번째 생기면 `components/`** 로 옮긴다.
  처음부터 컴포넌트로 빼지 않는다.
- 훅은 실제로 두 화면 이상에서 쓸 때 `hooks/` 로 올린다. 한 화면 전용이면 그 파일에 둔다.

## 컴포넌트

- **컴포넌트는 default export, 파일명은 PascalCase**다. 파일 하나에 컴포넌트 하나.

  ```jsx
  export default function VisibilityBadge({ visibility, size = 'md' }) { ... }
  ```

- **훅은 default export**다. 파일 하나에 훅 하나이며 파일명이 곧 훅 이름이다.

  ```js
  export default function usePagedList(load, enabled = true) { ... }
  ```

- **유틸·설정·컨텍스트·API 모듈은 named export**다 (`export const`, `export function`).
  한 파일이 값을 여럿 내보내므로 default 를 쓸 자리가 없다 — `config/uploadLimits.js` 는 8개,
  `context/GroupsContext.jsx` 는 프로바이더와 훅 둘, `api/groups.js` 는 엔드포인트마다 하나다.
- props 는 시그니처에서 구조 분해하고 기본값을 그 자리에 둔다.
- 조건부 렌더링이 3단 이상 중첩되면 이른 반환으로 펼친다.

## 스타일

- **일반 CSS 파일**을 쓴다. CSS Modules·Tailwind·CSS-in-JS 를 도입하지 않는다.
- 컴포넌트 `Foo.jsx` 의 스타일은 **같은 위치의 `Foo.css`** 에 두고 컴포넌트가 직접 import 한다.
- 클래스명은 kebab-case 이며 컴포넌트 이름을 접두사로 쓴다 (`visibility-badge-icon`).
- **색상은 CSS 변수로만 쓴다.** 라이트/다크 두 모드가 같은 변수를 공유해야 하므로 값을
  하드코딩하지 않는다. 브랜드 색은 파스텔 연두다 (명세 §6).

## 상태

- **서버 상태는 `GroupsContext`**(그룹·초대)가, **목업 상태는 `RecordsContext`**(여행·기록)가
  들고 있다. 화면 전용 상태만 `useState` 로 둔다.
- **서버 상태에 낙관적 갱신을 하지 않는다.** 정원 판정처럼 서버만 아는 규칙이 있어(공통 명세 §3.7)
  먼저 그려 두었다가 되돌리면 그 사이 화면이 거짓말을 한다. 쓰기가 성공하면 다시 읽는다.
- 페이지 단위 목록은 `usePagedList` 를 쓴다. "더 보기"가 필요한 다섯 목록이 같은 모양이다.
- **전역 상태 라이브러리를 새로 넣지 않는다.** Context로 부족해지면 먼저 논의한다.
- 사용자 선택(테마, 기준 위치, 지도 표시 방식)은 로컬 저장소에 보관하고 전용 훅으로 감싼다
  (`useTheme`, `useReferenceLocation`, `useMapMode`).
- 목록의 범위·필터·정렬·페이지는 **쿼리 파라미터로 유지**한다. 상세에서 돌아와도 목록 상태가
  보존되어야 한다.

## 공개 범위 UI — 가장 조심할 자리

이 서비스에서 실수가 가장 치명적인 지점이다. 명세 §9를 그대로 따른다.

- **공개 범위 기본 선택은 언제나 "나만 보기"** 다. 폼을 열었을 때 이미 공개로 선택된 상태를
  만들지 않는다.
- 공개 범위는 **여행에만 있다.** 기록 화면에 범위를 바꾸는 수단을 두지 않고, 기록에 표시되는
  범위는 소속 여행의 것임이 드러나야 한다.
- **색상만으로 세 상태를 구분하지 않는다.** 아이콘과 텍스트를 함께 쓴다.
- 범위를 **넓히는** 변경은 결과를 문장으로 확인시키고 영향받는 기록 수를 함께 말한다.
- 사진 URL은 공개 범위와 무관하게 접근 가능하므로(명세 §6.3), **"비공개 사진은 절대 노출되지
  않는다"고 오해할 문구를 쓰지 않는다.**

## API 연동

- 모든 요청은 `credentials: 'include'`, 쓰기 요청에는 `X-XSRF-TOKEN` 헤더를 붙인다.
- **오류 분기는 HTTP 상태가 아니라 응답의 `code` 로** 한다. 백엔드가 모든 실패를 같은 스키마로
  내려준다.
- 네이버 지역 검색·OAuth 키를 브라우저에 두지 않는다. 장소 검색은 **반드시 백엔드를 경유**한다.
  프론트엔드가 갖는 키는 지도 Client ID(`VITE_NAVER_MAP_CLIENT_ID`) 하나뿐이다.
- 백엔드와 같아야 하는 값(사진 제한 등)은 `config/` 에 환경변수로 외부화한다. 컴포넌트에
  숫자를 하드코딩하지 않는다.

## 접근성·반응형

- 모달은 `role="dialog"` / `aria-modal` 패턴을 지키고, 폼 입력에는 라벨을 준다.
- 공개 범위 라디오 그룹은 `fieldset` / `legend` 를 쓴다.
- 계산되어 바뀌는 값(여행 기간의 박·일 수 등)은 `aria-live="polite"` 로 읽히게 한다.
- **모바일 360px 부터** 데스크톱까지 대응한다.

## 현재 상태

**인증과 그룹·초대는 백엔드를 호출하고, 여행·기록·사진·태그는 아직 목업이다.** 목업 쪽은
`src/data/` 와 `RecordsContext` 로 동작하며 공개 범위 판정도 컨텍스트에서 서버와 같은 규칙으로
계산한다. **판정 규칙을 고칠 때는 서버와 어긋나지 않는지 확인한다** — 다만 실제 차단 책임은
서버에 있다.

**목업 여행의 `sharedGroupIds` 는 `MOCK_GROUPS` 의 id 를 가리킨다.** 그룹 화면이 쓰는 서버 id 와
다른 체계이므로, 여행 화면의 `myGroups` 만 목업을 본다 (명세 §10.2). 섞지 않는다.
