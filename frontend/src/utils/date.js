/**
 * 서버 시각(ISO)을 화면 표기로 바꾼다. 모두 이 브라우저의 시간대 기준이다 — UTC 로 자르면 새벽에
 * 남긴 기록의 날짜가 하루 밀린다.
 */

const pad = (n) => String(n).padStart(2, '0')

/** `2026-10-09` */
export function formatDate(iso) {
  const d = new Date(iso)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** `2026-10-09 18:12` */
export function formatDateTime(iso) {
  const d = new Date(iso)
  return `${formatDate(iso)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
