import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchReceivedInvites } from '../api/groups.js'
import { useAuth } from '../context/AuthContext.jsx'
import useDismissable from '../hooks/useDismissable.js'
import useResource from '../hooks/useResource.js'
import UserAvatar from './UserAvatar.jsx'
import { ChevronDownIcon } from './icons.jsx'
import './HeaderAuth.css'
import { AUTH_STATUS } from '../context/authStatus.js'

/** 받은 초대 건수 조회 */
const loadReceivedInvites = () => fetchReceivedInvites(0)

/** 헤더의 로그인 링크와 계정 메뉴 */
export default function HeaderAuth({ receivedCount: givenCount }) {
  const { user, status, logout } = useAuth()
  const fetched = useResource(loadReceivedInvites, status === AUTH_STATUS.AUTHENTICATED && givenCount === undefined)
  const receivedCount = givenCount ?? fetched.data?.totalElements ?? 0
  const [open, setOpen] = useState(false)
  const wrapRef = useDismissable(open, () => setOpen(false))
  const panelId = useId()

  // 로그인 확인 전에는 그리지 않는다
  if (status === AUTH_STATUS.LOADING) return null

  if (status !== AUTH_STATUS.AUTHENTICATED) {
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
        <UserAvatar user={user} size={28} />
        <span className="header-user-name">{user.name}</span>
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
