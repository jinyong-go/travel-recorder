// 사용자 선택(테마·지도 표시 방식·기준 위치)을 이 브라우저에 남기는 로컬 저장소 접근.
//
// 사생활 보호 모드나 저장 공간 초과에서는 localStorage 접근 자체가 예외를 던진다. 저장은 편의일 뿐이라
// 실패해도 화면은 기본값으로 계속 동작해야 하므로 여기서 삼킨다.

/** 저장된 문자열. 없거나 저장소를 쓸 수 없으면 null 이다. */
export function readStorage(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/** 문자열을 저장한다. 저장소를 쓸 수 없으면 아무 일도 하지 않는다. */
export function writeStorage(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 저장 공간을 사용할 수 없는 환경은 무시
  }
}
