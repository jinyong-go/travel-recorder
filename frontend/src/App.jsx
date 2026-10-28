import { Navigate, Route, Routes } from 'react-router-dom'
import './App.css'
import { AuthProvider } from './context/AuthContext.jsx'
import RequireAuth from './components/RequireAuth.jsx'
import GroupDetailPage from './pages/GroupDetailPage.jsx'
import InvitesPage from './pages/InvitesPage.jsx'
import GroupsPage from './pages/GroupsPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import LoginHistoryPage from './pages/LoginHistoryPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MyInfoPage from './pages/MyInfoPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import TripRegisterPage from './pages/TripRegisterPage.jsx'
import TripEditPage from './pages/TripEditPage.jsx'
import RecordDetailPage from './pages/RecordDetailPage.jsx'
import TripDetailPage from './pages/TripDetailPage.jsx'
import TripListPage from './pages/TripListPage.jsx'

/** 앱의 라우트 구성 */
function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/trips" element={<TripListPage />} />
        {/* 비로그인도 볼 수 있는 상세 화면 */}
        <Route path="/trips/:tripId" element={<TripDetailPage />} />
        <Route path="/records/:recordId" element={<RecordDetailPage />} />
        {/* 예전 기록 목록 주소는 여행 목록으로 보낸다 */}
        <Route path="/records" element={<Navigate to="/trips" replace />} />
        {/* 예전 기록 등록 주소는 여행 목록으로 보낸다 */}
        <Route path="/records/register" element={<Navigate to="/trips" replace />} />
        {/* 로그인이 필요한 화면 */}
        <Route element={<RequireAuth />}>
          <Route path="/trips/new" element={<TripRegisterPage />} />
          <Route path="/trips/:tripId/edit" element={<TripEditPage />} />
          <Route path="/me" element={<MyInfoPage />} />
          <Route path="/me/login-history" element={<LoginHistoryPage />} />
          <Route path="/invites" element={<Navigate to="/invites/received" replace />} />
          {/* 탭이 바뀌면 화면을 새로 그린다 */}
          <Route path="/invites/received" element={<InvitesPage key="received" tab="received" />} />
          <Route path="/invites/sent" element={<InvitesPage key="sent" tab="sent" />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/groups/:groupId" element={<GroupDetailPage />} />
        </Route>
        {/* 그 밖의 주소는 404 화면 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
