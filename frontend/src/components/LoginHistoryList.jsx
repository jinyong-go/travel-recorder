import { describeDevice } from '../utils/userAgent.js'
import './LoginHistoryList.css'

const pad = (n) => String(n).padStart(2, '0')

/**
 * "2026-10-09 18:12" 처럼 사용자 시간대의 날짜와 시:분. 모르는 로그인을 확인하는 목록이라
 * "3시간 전" 같은 상대 표기를 쓰지 않는다.
 */
const formatDateTime = (iso) => {
  const d = new Date(iso)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 로그인 이력 항목 목록. 조회·상태 처리는 쓰는 화면이 하고 여기서는 그리기만 한다.
 *
 * @param items `LoginHistoryItem[]`
 */
export default function LoginHistoryList({ items }) {
  return (
    <ul className="login-history-list">
      {items.map((item) => (
        <li key={item.id} className="login-history-item">
          <time className="login-history-time" dateTime={item.loggedInAt}>
            {formatDateTime(item.loggedInAt)}
          </time>
          {/* 판별이 틀렸을 때 확인할 수 있게 원문을 남긴다. */}
          <span className="login-history-device" title={item.userAgent ?? undefined}>
            {describeDevice(item.userAgent)}
          </span>
          <span className="login-history-ip">{item.ipAddress}</span>
        </li>
      ))}
    </ul>
  )
}
