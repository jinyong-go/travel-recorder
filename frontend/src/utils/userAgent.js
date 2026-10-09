/**
 * User-Agent 원문을 "Chrome · macOS" 같은 짧은 표기로 바꾼다. 화면 표기용이다.
 *
 * 원문은 길어 목록에서 읽을 수 없고, 서버는 해석하지 않고 원문을 준다. 라이브러리를 들이지 않고
 * 주요 브라우저·OS 만 가린다. 판별하지 못한 쪽은 "알 수 없음", 원문이 없으면 "알 수 없는 기기" 다.
 */

// 순서가 판정이다. Edge·Whale·삼성 인터넷·Opera 는 UA 에 Chrome 과 Safari 를 함께 적고,
// Chrome 은 Safari 를 함께 적으므로 더 구체적인 것을 먼저 본다.
const BROWSERS = [
  ['Edge', /Edg(A|iOS)?\//],
  ['Whale', /Whale\//],
  ['Samsung Internet', /SamsungBrowser\//],
  ['Opera', /OPR\//],
  ['Firefox', /Firefox\/|FxiOS\//],
  ['Chrome', /Chrome\/|CriOS\//],
  ['Safari', /Version\/.*Safari\//],
]

// iPhone·iPad UA 에는 "Mac OS X" 가 들어 있어 iOS 를 macOS 보다 먼저 본다.
// Android UA 에는 "Linux" 가 들어 있어 Android 를 Linux 보다 먼저 본다.
const OPERATING_SYSTEMS = [
  ['Windows', /Windows/],
  ['iOS', /iPhone|iPad|iPod/],
  ['Android', /Android/],
  ['ChromeOS', /CrOS/],
  ['macOS', /Mac OS X|Macintosh/],
  ['Linux', /Linux/],
]

const match = (table, userAgent) => table.find(([, pattern]) => pattern.test(userAgent))?.[0]

export function describeDevice(userAgent) {
  if (!userAgent) return '알 수 없는 기기'
  const browser = match(BROWSERS, userAgent) ?? '알 수 없음'
  const os = match(OPERATING_SYSTEMS, userAgent) ?? '알 수 없음'
  return `${browser} · ${os}`
}
