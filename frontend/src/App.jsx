import { Route, Routes } from 'react-router-dom'
import './App.css'
import { RecordsProvider } from './context/RecordsContext.jsx'
import GroupDetailPage from './pages/GroupDetailPage.jsx'
import GroupsPage from './pages/GroupsPage.jsx'
import InviteAcceptPage from './pages/InviteAcceptPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import RecordDetailPage from './pages/RecordDetailPage.jsx'
import RecordListPage from './pages/RecordListPage.jsx'
import RegisterRecordPage from './pages/RegisterRecordPage.jsx'

function App() {
  return (
    <RecordsProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/records" element={<RecordListPage />} />
        <Route path="/records/register" element={<RegisterRecordPage />} />
        {/* react-router 는 구체적인 경로를 우선 매칭하므로 /records/register 가 recordId 로 잡히지 않는다. */}
        <Route path="/records/:recordId" element={<RecordDetailPage />} />
        <Route path="/groups" element={<GroupsPage />} />
        <Route path="/groups/:groupId" element={<GroupDetailPage />} />
        {/* 초대 링크는 비로그인으로도 열려야 한다 (어떤 그룹 초대인지 먼저 보여준 뒤 로그인 유도). */}
        <Route path="/invites/:token" element={<InviteAcceptPage />} />
        {/* 위 경로에 매칭되지 않는 모든 주소는 404 페이지로 보낸다. */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </RecordsProvider>
  )
}

export default App
