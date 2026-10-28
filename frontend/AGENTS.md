# frontend/AGENTS.md

## 명령

```bash
npm run dev       # 개발 서버 (5173 — compose 의 web 과 겹친다)
npm run lint      # oxlint
npm run build     # 프로덕션 빌드
```

- 테스트는 없다. 변경 후 lint·build 로 확인한다.

## 결정

- 순수 JavaScript 로 쓴다. TypeScript 는 도입하지 않는다.
- 일반 CSS 만 쓰고, 색상은 CSS 변수로만 쓴다.
- 전역 상태는 `AuthContext` 하나다. 서버 데이터는 쓰는 화면이 직접 조회한다.
- 조회는 `useResource`·`usePagedList` 로 한다. 화면에서 `useEffect` 로 직접 조회하지 않는다.
- 낙관적 갱신은 하지 않고, 쓰기에 성공하면 다시 읽는다.
- 컴포넌트는 두 곳 이상에서 쓰일 때 `components/` 로 옮긴다.

## API 연동

- 요청은 `api/client.js` 를 거치고, 경로에 값을 넣을 때는 `apiPath` 를 쓴다.
- 오류는 응답의 `code` 로 분기하고, 문구는 `errorMessage` 로 고른다.
- 비밀 키는 브라우저에 두지 않는다. `VITE_` 환경변수는 번들에 그대로 들어간다.
- 백엔드와 맞춰야 하는 값은 `config/` 에 둔다.

## 주의

- 개발 모드에서 조회가 두 번 나가는 것은 StrictMode 때문이다. 고치지 않는다.
- 공개 범위는 서버가 판정한다. 화면은 404 를 "없음"으로만 그린다.
