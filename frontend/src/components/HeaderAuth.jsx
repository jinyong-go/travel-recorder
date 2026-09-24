import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { useGroups } from '../context/GroupsContext.jsx'
import { ChevronDownIcon } from './icons.jsx'
import './HeaderAuth.css'

/**
 * 헤더 우상단의 인증 영역 (명세 §2.1).
 * 비로그인이면 로그인 링크를, 로그인 상태면 계정 메뉴(내 정보·그룹 관리·초대함·로그아웃)를 그린다.
 *
 * 로그인해야만 쓰는 진입점을 여기에 모아, 비로그인 헤더에서 한꺼번에 빠지게 한다.
 */
export default function HeaderAuth() {
  const { user, status, logout } = useAuth()
  const { receivedCount } = useGroups()
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const panelId = useId()

  // 열려 있을 때만 바깥 클릭과 Esc 를 듣는다.
  useEffect(() => {
    if (!open) return undefined

    const handleClickOutside = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false)
    }
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  // 세션 확인 전에는 아무것도 그리지 않는다. 로그인 링크를 먼저 보여 주면
  // 로그인한 사용자에게 화면이 한 번 깜빡인다.
  if (status === 'loading') return null

  if (status !== 'authenticated') {
    return (
      <Link to="/login" className="header-login-link">
        로그인
      </Link>
    )
  }

  const close = () => setOpen(false)
  const handleLogout = () => {
    close()
    logout()
  }

  const badge = receivedCount > 0 && (
    <span className="header-invite-badge" aria-hidden="true">
      {receivedCount}
    </span>
  )

  return (
    <div className="account-menu" ref={wrapRef}>
      <button
        type="button"
        className="account-menu-btn"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={
          receivedCount > 0
            ? `${user.name} 계정 메뉴 (받은 초대 ${receivedCount}건)`
            : `${user.name} 계정 메뉴`
        }
      >
        {user.profileImageUrl ? (
          <img className="header-user-avatar" src={user.profileImageUrl} alt="" />
        ) : (
          <span className="header-user-avatar header-user-avatar-fallback" aria-hidden="true">
            {user.name.slice(0, 1)}
          </span>
        )}
        <span className="header-user-name">{user.name}</span>
        {/* 메뉴를 열기 전에도 초대가 온 것이 보여야 한다 (명세 §2.1). */}
        {badge}
        <ChevronDownIcon />
      </button>

      {open && (
        <div id={panelId} className="account-menu-panel">
          <Link to="/me" className="account-menu-item" onClick={close}>
            내 정보
          </Link>
          <Link to="/groups" className="account-menu-item" onClick={close}>
            그룹 관리
          </Link>
          <Link
            to="/invites"
            className="account-menu-item"
            onClick={close}
            aria-label={receivedCount > 0 ? `초대함 (받은 초대 ${receivedCount}건)` : undefined}
          >
            초대함
            {badge}
          </Link>
          <hr className="account-menu-divider" />
          <button type="button" className="account-menu-item" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      )}
    </div>
  )
}
