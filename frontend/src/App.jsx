import { Route, Routes } from 'react-router-dom'
import './App.css'
import { PlacesProvider } from './context/PlacesContext.jsx'
import LandingPage from './pages/LandingPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MainPage from './pages/MainPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import PlaceDetailPage from './pages/PlaceDetailPage.jsx'
import RegisterPlacePage from './pages/RegisterPlacePage.jsx'

function App() {
  return (
    <PlacesProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/places" element={<MainPage />} />
        <Route path="/places/register" element={<RegisterPlacePage />} />
        {/* react-router 는 구체적인 경로를 우선 매칭하므로 /places/register 가 placeId 로 잡히지 않는다. */}
        <Route path="/places/:placeId" element={<PlaceDetailPage />} />
        {/* 위 경로에 매칭되지 않는 모든 주소는 404 페이지로 보낸다. */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </PlacesProvider>
  )
}

export default App
