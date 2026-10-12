import { Link } from 'react-router-dom'
import { ArrowLeftIcon } from './icons.jsx'
import '../styles/detailPage.css'

/**
 * 상세 화면이 내용을 그리기 전의 자리 — 불러오는 중, 없음, 실패 안내를 한 문장으로 보인다.
 * `backTo` 를 주면 `backLabel` 로 돌아갈 링크를 단다.
 */
export default function DetailStatus({ title, desc, backTo, backLabel }) {
  return (
    <main className="detail-page">
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
