import './StarRatingInput.css'

export default function StarRatingInput({ value, onChange }) {
  return (
    <div className="star-rating-input" role="radiogroup" aria-label="평점">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n}점`}
          className={`star-btn${n <= value ? ' filled' : ''}`}
          onClick={() => onChange(n)}
        >
          ★
        </button>
      ))}
      {value > 0 && <span className="star-rating-value">{value.toFixed(1)}</span>}
    </div>
  )
}
