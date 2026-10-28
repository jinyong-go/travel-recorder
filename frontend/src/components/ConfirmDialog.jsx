import useModal from '../hooks/useModal.js'
import '../styles/confirmPanel.css'

/** 동작을 실행하기 전에 띄우는 확인 모달 */
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
