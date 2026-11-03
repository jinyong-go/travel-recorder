import { NavLink, Outlet } from 'react-router-dom'
import './AdminLayout.css'

const MENU = [
  { to: '/', label: '대시보드', end: true },
  { to: '/users', label: '사용자' },
]

/** 왼쪽 메뉴와 상단 바를 두른 관리자 화면 틀 */
export default function AdminLayout() {
  return (
    <div className="admin-layout">
      <aside className="admin-sidebar">
        <p className="admin-brand">여행 지도 관리자</p>
        <nav aria-label="관리 메뉴">
          <ul className="admin-menu">
            {MENU.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.end} className="admin-menu-link">
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      <div className="admin-body">
        <header className="admin-topbar">
          {/* 실제 데이터로 오해하지 않도록 백엔드 연동 전까지 늘 보인다. */}
          <span className="admin-mock-badge">목업 데이터</span>
        </header>
        <main className="admin-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
