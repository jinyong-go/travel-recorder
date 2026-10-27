import useModal from '../hooks/useModal.js'
import '../styles/confirmPanel.css'

/**
 * 되돌리기 어렵거나 결과를 먼저 알려야 하는 동작 앞의 확인 모달.
 *
 * `children` 은 확인을 누르면 무엇이 어떻게 되는지 설명하는 문장이다. `busy` 동안 확인 버튼을
 * 막고 문구를 `busyLabel` 로 바꾼다 — 두 번 눌려 같은 요청이 두 번 나가지 않게 한다. 취소와
 * 바깥 클릭은 막지 않는다. `safe` 면 확인 버튼에 삭제의 붉은색을 쓰지 않는다.
 */
export default function ConfirmDialog({
  title,
  children,
  error,
  confirmLabel,
  busyLabel = confirmLabel,
  busy = false,
  safe = false,
  onConfirm,
  onCancel,
}) {
  const panelRef = useModal(onCancel)
  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div
        ref={panelRef}
        className="modal-panel confirm-panel"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <h2>{title}</h2>
        <p className="confirm-desc">{children}</p>
        {error && <p className="field-error">{error}</p>}
        <div className="confirm-actions">
          <button type="button" className="btn-secondary" onClick={onCancel}>
            취소
          </button>
          <button
            type="button"
            className={safe ? 'btn-primary' : 'btn-danger'}
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
