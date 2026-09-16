import { useEffect, useRef, useState } from 'react'
import { MAP_MODES, hasNaverMapClientId } from '../config/mapSettings.js'
import { ChevronDownIcon, GearIcon } from './icons.jsx'

export default function SettingsMenu({ mapMode, onChangeMapMode }) {
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div className="settings-menu" ref={wrapRef}>
      <button
        type="button"
        className="settings-menu-btn"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <GearIcon />
        설정
        <ChevronDownIcon />
      </button>

      {open && (
        <div className="settings-panel" role="dialog" aria-label="설정">
          <p className="settings-panel-title">지도 표시 방식</p>
          <ul className="settings-option-list">
            {MAP_MODES.map((mode) => {
              const disabled = mode.key === 'embed' && !hasNaverMapClientId
              return (
                <li key={mode.key}>
                  <button
                    type="button"
                    className={`settings-option${mode.key === mapMode ? ' active' : ''}`}
                    disabled={disabled}
                    onClick={() => onChangeMapMode(mode.key)}
                  >
                    <span className="settings-option-label">{mode.label}</span>
                    <span className="settings-option-desc">
                      {disabled
                        ? '네이버 지도 Client ID 가 설정되지 않아 사용할 수 없어요. (.env의 VITE_NAVER_MAP_CLIENT_ID)'
                        : mode.description}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </div>
  )
}
