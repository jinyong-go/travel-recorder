import { Link } from 'react-router-dom'

/** 없는 주소로 들어왔을 때 */
export default function NotFoundPage() {
  return (
    <>
      <h1 className="page-title">페이지를 찾을 수 없어요</h1>
      <p className="note">
        <Link to="/">대시보드로 돌아가기</Link>
      </p>
    </>
  )
}
