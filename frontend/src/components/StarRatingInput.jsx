import { useState } from 'react'
import './StarRatingInput.css'

const STARS = [1, 2, 3, 4, 5]

// 별 하나당 좌/우 절반을 각각 눌러 0.5 단위(0.5 ~ 5.0)로 평점을 매긴다.
export default function StarRatingInput({ value, onChange }) {
  const [hoverValue, setHoverValue] = useState(0)
  const shown = hoverValue || value

  return (
    <div
      className="star-rating-input"
      role="radiogroup"
      aria-label="평점"
      onMouseLeave={() => setHoverValue(0)}
    >
      <div className="star-row">
        {STARS.map((n) => {
          // 현재 값 기준 이 별이 채워져야 하는 비율 (0 ~ 1)
          const fill = Math.min(Math.max(shown - (n - 1), 0), 1)
          return (
            <span className="star-slot" key={n}>
              <span className="star-base" aria-hidden="true">
                ★
              </span>
              <span
                className="star-fill"
                style={{ width: `${fill * 100}%` }}
                aria-hidden="true"
              >
                ★
              </span>
              {[n - 0.5, n].map((score) => (
                <button
                  key={score}
                  type="button"
                  role="radio"
                  aria-checked={value === score}
                  aria-label={`${score}점`}
                  className={`star-half${score === n ? ' right' : ' left'}`}
                  onClick={() => onChange(score)}
                  onMouseEnter={() => setHoverValue(score)}
                  onFocus={() => setHoverValue(score)}
                  onBlur={() => setHoverValue(0)}
                />
              ))}
            </span>
          )
        })}
      </div>
      {value > 0 && <span className="star-rating-value">{value.toFixed(1)}</span>}
    </div>
  )
}
