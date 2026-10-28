/** User-Agent 를 "Chrome · macOS" 같은 짧은 표기로 바꾼다 */

/** 브라우저 판별 규칙. 더 구체적인 것을 먼저 본다. */
const BROWSERS = [
  ['Edge', /Edg(A|iOS)?\//],
  ['Whale', /Whale\//],
  ['Samsung Internet', /SamsungBrowser\//],
  ['Opera', /OPR\//],
  ['Firefox', /Firefox\/|FxiOS\//],
  ['Chrome', /Chrome\/|CriOS\//],
  ['Safari', /Version\/.*Safari\//],
]

/** OS 판별 규칙. 더 구체적인 것을 먼저 본다. */
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
