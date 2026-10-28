# frontend/AGENTS.md

프론트엔드에만 해당하는 규칙이다. 공통 규칙은 [루트 AGENTS.md](../AGENTS.md)에 있다.

## 명령

```bash
npm run dev       # 개발 서버
npm run lint      # oxlint
npm run build     # 프로덕션 빌드
```

## 스택·스타일

- React / Vite / react-router, 순수 JavaScript 로 쓴다. TypeScript 는 도입하지 않는다.
- 세미콜론 없이 작은따옴표를 쓴다.

## 파일 배치

- 라우트 화면은 `pages/`, 두 곳 이상에서 쓰는 컴포넌트는 `components/`, 훅은 `hooks/` 에 둔다.
- 컴포넌트·훅은 파일 하나에 하나씩 default export 하고, 유틸·API 모듈은 named export 한다.

## CSS

- 일반 CSS 만 쓴다. 컴포넌트 스타일은 옆의 같은 이름 `.css` 에 둔다.
- 공유 스타일은 `App.css`·`styles/` 에 둔다.
- 클래스명은 컴포넌트 이름을 접두사로 한 kebab-case 다.
- 색상은 CSS 변수로만 쓴다.

## 상태

- 서버 데이터는 쓰는 화면이 직접 조회한다. 전역 상태는 `AuthContext` 하나만 둔다.
- 조회는 `useResource`·`usePagedList` 로 한다.
- 낙관적 갱신은 하지 않고, 쓰기에 성공하면 다시 읽는다.

## API 연동

- 요청은 `api/client.js` 를 거친다.
- 오류는 응답의 `code` 로 분기하고, 문구는 `errorMessage` 로 고른다.
- API 키는 브라우저에 두지 않는다.
- 백엔드와 맞춰야 하는 값은 `config/` 에 둔다.
