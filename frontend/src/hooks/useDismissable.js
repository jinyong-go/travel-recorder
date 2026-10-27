import { useEffect, useRef } from 'react'

/**
 * 열린 드롭다운을 바깥 클릭과 Esc 로 닫는다. 돌려받은 ref 를 드롭다운을 감싼 요소에 단다.
 * 열려 있을 때만 듣는다 — 닫힌 메뉴가 화면의 모든 클릭을 받을 이유가 없다.
 */
export default function useDismissable(open, onClose) {
  const ref = useRef(null)
  // 처리기를 열릴 때 한 번만 붙이므로, 렌더링마다 바뀌는 onClose 는 ref 로 최신 값을 읽는다.
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    if (!open) return undefined

    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onCloseRef.current()
    }
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onCloseRef.current()
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  return ref
}
