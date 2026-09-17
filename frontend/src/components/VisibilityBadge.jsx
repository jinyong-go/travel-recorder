import { visibilityMeta } from '../data/records.js'
import { GlobeIcon, LockIcon, UsersIcon } from './icons.jsx'
import './VisibilityBadge.css'

const ICONS = { lock: LockIcon, users: UsersIcon, globe: GlobeIcon }

/**
 * 공개 범위 배지. 작성자 본인의 기록에만 노출한다.
 *
 * 색상만으로 세 상태를 구분하지 않고 아이콘과 텍스트를 함께 쓴다.
 * 여기서 잘못 읽히면 곧바로 "공개인 줄 몰랐다" 로 이어지는 자리다.
 */
export default function VisibilityBadge({ visibility, size = 'md' }) {
  const meta = visibilityMeta(visibility)
  const Icon = ICONS[meta.icon] ?? LockIcon

  return (
    <span className={`visibility-badge visibility-${meta.key.toLowerCase()} visibility-badge-${size}`}>
      <Icon className="visibility-badge-icon" />
      {meta.label}
    </span>
  )
}
