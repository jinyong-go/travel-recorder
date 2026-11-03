/** 서버 시각을 브라우저 시간대 기준으로 표기한다 */

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
