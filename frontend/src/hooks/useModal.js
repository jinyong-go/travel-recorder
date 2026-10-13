import { useEffect, useRef } from 'react'

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

// 열려 있는 모달을 연 순서대로 쌓는다. 모달이 겹쳐 있으면(등록 모달 위의 장소 검색) Esc·Tab 은
// 맨 위 하나만 받는다 — 그러지 않으면 Esc 한 번에 두 모달이 함께 닫힌다.
const openModals = []

/**
 * 모달 패널에 키보드 동작을 붙인다. Esc 로 닫고, Tab 이 패널 밖으로 나가지 않게 가두고, 열릴 때
 * 패널 안으로 포커스를 옮기고, 닫힌 뒤에는 열기 전에 있던 요소로 포커스를 돌려놓는다.
 *
 * 돌려받은 ref 를 `role="dialog"` 요소에 단다. `onClose` 가 없으면 Esc 로 닫지 않는다 — 제출 중이라
 * 바깥 클릭도 막아 둔 경우다. 늘 렌더링되는 화면에 모달이 조건부로 붙으면 `open` 으로 켜고 끈다.
 * 패널 안에 이미 포커스가 있으면(autoFocus) 처음 포커스는 건드리지 않는다.
 */
export default function useModal(onClose, open = true) {
  const panelRef = useRef(null)
  // 키 처리기를 열릴 때 한 번만 붙이므로, 렌더링마다 바뀌는 onClose 는 ref 로 최신 값을 읽는다.
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
      // 한글 조합 중의 Esc 는 조합을 끝내는 키다. 모달을 닫으면 입력하던 글자를 잃는다.
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
      // 오버레이를 눌러 포커스가 패널 밖에 있으면 다음 Tab 은 패널의 처음으로 들어온다.
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
      // 연 버튼이 화면에서 사라졌으면(삭제 후 이동) 돌려놓을 곳이 없으므로 그대로 둔다.
      if (opener?.isConnected) opener.focus()
    }
  }, [open])

  return panelRef
}
