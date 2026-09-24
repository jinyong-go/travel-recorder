import { Navigate, Route, Routes } from 'react-router-dom'
import './App.css'
import { AuthProvider } from './context/AuthContext.jsx'
import RequireAuth from './components/RequireAuth.jsx'
import GroupDetailPage from './pages/GroupDetailPage.jsx'
import InvitesPage from './pages/InvitesPage.jsx'
import GroupsPage from './pages/GroupsPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MyInfoPage from './pages/MyInfoPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import TripRegisterPage from './pages/TripRegisterPage.jsx'
import RecordDetailPage from './pages/RecordDetailPage.jsx'
import TripDetailPage from './pages/TripDetailPage.jsx'
import TripListPage from './pages/TripListPage.jsx'

function App() {
  return (
    // 전역 상태는 로그인 상태 하나다. 여행·기록·그룹은 쓰는 화면이 직접 읽는다 (명세 §10.2).
    <AuthProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/trips" element={<TripListPage />} />
        {/* 여행·기록 상세는 막지 않는다. 전체 공개 여행은 비로그인도 봐야 한다 (명세 §2.1). */}
        <Route path="/trips/:tripId" element={<TripDetailPage />} />
        <Route path="/records/:recordId" element={<RecordDetailPage />} />
        {/* 기록 목록은 여행 목록으로 대체됐다. 남아 있는 링크·북마크를 404 로 떨어뜨리지 않는다. */}
        <Route path="/records" element={<Navigate to="/trips" replace />} />
        {/* 기록 등록은 여행 상세의 모달로 옮겼다 (명세 §5.3). /records/:recordId 로 잡혀
            "기록 없음" 이 되지 않도록 먼저 받아 여행 목록으로 보낸다. */}
        <Route path="/records/register" element={<Navigate to="/trips" replace />} />
        {/* 로그인이 필요한 화면 (명세 §2.1). */}
        <Route element={<RequireAuth />}>
          {/* react-router 는 구체적인 경로를 우선 매칭하므로 /trips/new 가 tripId 로 잡히지 않는다. */}
          <Route path="/trips/new" element={<TripRegisterPage />} />
          <Route path="/me" element={<MyInfoPage />} />
          {/* 받은/보낸은 담기는 항목과 동작이 다른 별개 목록이라 경로로 나눈다 (명세 §5.8.4). */}
          <Route path="/invites" element={<Navigate to="/invites/received" replace />} />
          <Route path="/invites/received" element={<InvitesPage tab="received" />} />
          <Route path="/invites/sent" element={<InvitesPage tab="sent" />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/groups/:groupId" element={<GroupDetailPage />} />
        </Route>
        {/* 위 경로에 매칭되지 않는 모든 주소는 404 페이지로 보낸다. */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
