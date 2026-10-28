import { formatDateTime } from '../utils/date.js'
import { describeDevice } from '../utils/userAgent.js'
import './LoginHistoryList.css'

/** 로그인 이력 목록 */
export default function LoginHistoryList({ items }) {
  return (
    <ul className="login-history-list">
      {items.map((item) => (
        <li key={item.id} className="login-history-item">
          <time className="login-history-time" dateTime={item.loggedInAt}>
            {formatDateTime(item.loggedInAt)}
          </time>
          <span className="login-history-device" title={item.userAgent ?? undefined}>
            {describeDevice(item.userAgent)}
          </span>
          <span className="login-history-ip">{item.ipAddress}</span>
        </li>
      ))}
    </ul>
  )
}
