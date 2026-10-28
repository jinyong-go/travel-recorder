import { useEffect, useRef } from 'react'

/** 드롭다운을 바깥 클릭과 Esc 로 닫는다 */
export default function useDismissable(open, onClose) {
  const ref = useRef(null)
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
