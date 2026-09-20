import { Navigate, Route, Routes } from 'react-router-dom'
import './App.css'
import { RecordsProvider } from './context/RecordsContext.jsx'
import GroupDetailPage from './pages/GroupDetailPage.jsx'
import GroupsPage from './pages/GroupsPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import TripRegisterPage from './pages/TripRegisterPage.jsx'
import RecordDetailPage from './pages/RecordDetailPage.jsx'
import RegisterRecordPage from './pages/RegisterRecordPage.jsx'
import TripDetailPage from './pages/TripDetailPage.jsx'
import TripListPage from './pages/TripListPage.jsx'

function App() {
  return (
    <RecordsProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/trips" element={<TripListPage />} />
        {/* react-router 는 구체적인 경로를 우선 매칭하므로 /trips/new 가 tripId 로 잡히지 않는다. */}
        <Route path="/trips/new" element={<TripRegisterPage />} />
        <Route path="/trips/:tripId" element={<TripDetailPage />} />
        <Route path="/records/register" element={<RegisterRecordPage />} />
        <Route path="/records/:recordId" element={<RecordDetailPage />} />
        {/* 기록 목록은 여행 목록으로 대체됐다. 남아 있는 링크·북마크를 404 로 떨어뜨리지 않는다. */}
        <Route path="/records" element={<Navigate to="/trips" replace />} />
        <Route path="/groups" element={<GroupsPage />} />
        <Route path="/groups/:groupId" element={<GroupDetailPage />} />
        {/* 위 경로에 매칭되지 않는 모든 주소는 404 페이지로 보낸다. */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </RecordsProvider>
  )
}

export default App
