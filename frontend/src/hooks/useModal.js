import { useEffect, useRef } from 'react'

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** 열려 있는 모달 목록 */
const openModals = []

/** 모달의 Esc 닫기와 포커스 관리 */
export default function useModal(onClose, open = true) {
  const panelRef = useRef(null)
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    if (!open) return
    const panel = panelRef.current
    const opener = document.activeElement
    openModals.push(panel)

    if (!panel.contains(document.activeElement)) panel.querySelector(FOCUSABLE)?.focus()

    const handleKeyDown = (e) => {
      if (openModals.at(-1) !== panel) return
      // 한글 조합 중의 Esc 는 무시한다
      if (e.key === 'Escape' && !e.isComposing) {
        if (onCloseRef.current) {
          e.preventDefault()
          onCloseRef.current()
        }
        return
      }
      if (e.key !== 'Tab') return

      const focusables = [...panel.querySelectorAll(FOCUSABLE)]
      const first = focusables[0]
      const last = focusables.at(-1)
      if (!panel.contains(document.activeElement)) {
        e.preventDefault()
        first?.focus()
      } else if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      openModals.splice(openModals.indexOf(panel), 1)
      if (opener?.isConnected) opener.focus()
    }
  }, [open])

  return panelRef
}
