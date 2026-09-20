import { Link } from 'react-router-dom'
import { ArrowLeftIcon, MapPinIcon } from '../components/icons.jsx'
import './NotFoundPage.css'

export default function NotFoundPage() {
  return (
    <main className="notfound-page">
      <div className="notfound-panel">
        <MapPinIcon className="notfound-icon" width="44" height="44" />
        <p className="notfound-code">404</p>
        <h1 className="notfound-title">길을 잃은 것 같아요</h1>
        <p className="notfound-desc">
          요청하신 주소를 찾을 수 없습니다.
          <br />
          주소가 바뀌었거나 삭제된 페이지일 수 있어요.
        </p>
        <Link to="/" className="notfound-home-btn">
          <ArrowLeftIcon /> 홈으로
        </Link>
        <Link to="/trips" className="notfound-places-link">
          여행 목록 보기
        </Link>
      </div>
    </main>
  )
}
