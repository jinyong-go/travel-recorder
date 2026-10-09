/**
 * 폼 위의 필수 항목 안내. 라벨 옆 붉은 `*`(`.field-required`)가 무엇을 뜻하는지 알린다.
 * 선택 항목에는 따로 표시를 두지 않으므로, 이 안내가 있어야 `*` 가 없는 칸이 선택임을 알 수 있다.
 */
export default function RequiredNote() {
  return (
    <p className="field-hint">
      <span className="required-mark" aria-hidden="true">
        *
      </span>{' '}
      표시는 필수 항목입니다.
    </p>
  )
}
