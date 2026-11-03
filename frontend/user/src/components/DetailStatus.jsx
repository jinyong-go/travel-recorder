import { Link } from 'react-router-dom'
import { ArrowLeftIcon } from './icons.jsx'
import '../styles/page.css'
import '../styles/detailPage.css'

/** 상세 화면의 불러오는 중·없음·실패 안내 */
export default function DetailStatus({ title, desc, backTo, backLabel }) {
  return (
    <main className="page detail-status">
      <div className="detail-missing">
        <h1>{title}</h1>
        {desc && <p className="detail-missing-desc">{desc}</p>}
        {backTo && (
          <Link to={backTo} className="detail-missing-link">
            <ArrowLeftIcon /> {backLabel}
          </Link>
        )}
      </div>
    </main>
  )
}
