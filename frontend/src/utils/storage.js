/** 로컬 저장소 읽기·쓰기. 쓸 수 없으면 조용히 넘어간다. */

/** 저장된 값. 없으면 null. */
export function readStorage(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/** 값을 저장한다 */
export function writeStorage(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 저장 공간을 사용할 수 없는 환경은 무시
  }
}
