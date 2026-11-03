import { fileUrl } from '../api/client.js'
import { tripMonthLabel } from '../data/trips.js'
import './TripCover.css'

/** 여행 카드의 대표 사진. 없으면 시작 연·월을 보인다. */
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
