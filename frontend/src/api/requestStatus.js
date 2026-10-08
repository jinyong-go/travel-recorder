/**
 * 서버 조회 하나의 진행 상태. 화면과 훅이 같은 값으로 분기하도록 한곳에 둔다.
 * 문자열을 직접 쓰면 오타가 나도 조용히 "어느 분기에도 안 걸리는" 상태가 된다.
 *
 * IDLE 은 조회할 대상이 없어 요청을 보내지 않은 상태, MISSING 은 서버가 404 로 답한 상태다.
 */
export const REQUEST_STATUS = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  COMPLETE: 'complete',
  MISSING: 'missing',
  ERROR: 'error',
})
