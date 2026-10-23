import { fileUrl } from '../api/client.js'
import { tripMonthLabel } from '../data/trips.js'
import './TripCover.css'

/**
 * 여행 카드의 대표 사진 자리. 지정된 사진이 없으면 시작 연·월을 적는다 — 서버는 대체 이미지를
 * 고르지 않으므로 화면이 정한다. 연·월은 카드의 기간 줄과 겹치므로 보조기술에는 읽히지 않게 한다.
 */
export default function TripCover({ trip }) {
  return (
    <div className="trip-cover">
      {trip.coverPhotoUrl ? (
        <img src={fileUrl(trip.coverPhotoUrl)} alt="" className="trip-cover-img" />
      ) : (
        <span className="trip-cover-month" aria-hidden="true">
          {tripMonthLabel(trip)}
        </span>
      )}
    </div>
  )
}
