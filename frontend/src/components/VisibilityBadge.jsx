import { visibilityMeta } from '../data/visibility.js'
import { VisibilityIcon } from './icons.jsx'
import './VisibilityBadge.css'

/** 공개 범위 배지 */
export default function VisibilityBadge({ visibility, size = 'md' }) {
  const meta = visibilityMeta(visibility)

  return (
    <span className={`visibility-badge visibility-${meta.key.toLowerCase()} visibility-badge-${size}`}>
      <VisibilityIcon visibility={meta.key} className="visibility-badge-icon" />
      {meta.label}
    </span>
  )
}
