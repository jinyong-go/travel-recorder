import { useState } from 'react'
import useDismissable from '../hooks/useDismissable.js'
import { MAP_MODES, hasNaverMapClientId } from '../config/mapSettings.js'
import { ChevronDownIcon, GearIcon } from './icons.jsx'
import './SettingsMenu.css'

/**
 * 헤더의 설정 드롭다운. 지금은 지도 표시 방식(새 창 / 페이지 내 지도) 하나를 고른다.
 * 네이버 지도 Client ID 가 없으면 페이지 내 지도는 고를 수 없게 막고 이유를 적는다.
 */
export default function SettingsMenu({ mapMode, onChangeMapMode }) {
  const [open, setOpen] = useState(false)
  const wrapRef = useDismissable(open, () => setOpen(false))

  return (
    <div className="settings-menu" ref={wrapRef}>
      <button
        type="button"
        className="settings-menu-btn"
        aria-label="설정"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <GearIcon />
        <span className="settings-menu-label">설정</span>
        <ChevronDownIcon className="settings-menu-chevron" />
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
