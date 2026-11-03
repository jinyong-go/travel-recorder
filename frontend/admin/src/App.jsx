import { Route, Routes } from 'react-router-dom'
import AdminLayout from './components/AdminLayout.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import UsersPage from './pages/UsersPage.jsx'

/** 관리자 웹의 라우트 */
export default function App() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
