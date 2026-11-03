/** 필수 항목 표시(*) 안내 */
export default function RequiredNote() {
  return (
    <p className="field-hint">
      <span className="required-mark" aria-hidden="true">
        *
      </span>{' '}
      표시는 필수 항목이에요.
    </p>
  )
}
