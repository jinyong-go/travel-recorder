import { useEffect, useRef, useState } from 'react'
import { THEMES } from '../theme/themes.js'
import { ChevronDownIcon, PaletteIcon } from './icons.jsx'

export default function ThemeSwitcher({ themeKey, onChange }) {
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

  const current = THEMES.find((t) => t.key === themeKey) ?? THEMES[0]

  return (
    <div className="theme-switcher" ref={wrapRef}>
      <button
        type="button"
        className="theme-switcher-btn"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <PaletteIcon />
        <span className="theme-switcher-current" style={{ background: current.swatch }} />
        테마
        <ChevronDownIcon />
      </button>

      {open && (
        <ul className="theme-menu" role="listbox">
          {THEMES.map((t) => (
            <li key={t.key}>
              <button
                type="button"
                className={`theme-menu-item${t.key === themeKey ? ' active' : ''}`}
                role="option"
                aria-selected={t.key === themeKey}
                onClick={() => {
                  onChange(t.key)
                  setOpen(false)
                }}
              >
                <span className="theme-swatch" style={{ background: t.swatch }} />
                {t.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
