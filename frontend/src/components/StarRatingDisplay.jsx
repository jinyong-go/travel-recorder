import './StarRatingInput.css'
import './StarRatingDisplay.css'

const STARS = [1, 2, 3, 4, 5]

/** 읽기 전용 별점 */
export default function StarRatingDisplay({ value, size = 16 }) {
  return (
    <span className="star-rating-display" aria-label={`${value.toFixed(1)}점`}>
      <span className="star-row" style={{ fontSize: `${size}px` }} aria-hidden="true">
        {STARS.map((n) => {
          const fill = Math.min(Math.max(value - (n - 1), 0), 1)
          return (
            <span className="star-slot" key={n}>
              <span className="star-base">★</span>
              <span className="star-fill" style={{ width: `${fill * 100}%` }}>
                ★
              </span>
            </span>
          )
        })}
      </span>
      <span className="star-rating-display-value" aria-hidden="true">
        {value.toFixed(1)}
      </span>
    </span>
  )
}
