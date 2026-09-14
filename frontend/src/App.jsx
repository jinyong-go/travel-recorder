import { Route, Routes } from 'react-router-dom'
import './App.css'
import { PlacesProvider } from './context/PlacesContext.jsx'
import MainPage from './pages/MainPage.jsx'
import RegisterPlacePage from './pages/RegisterPlacePage.jsx'

function App() {
  return (
    <PlacesProvider>
      <Routes>
        <Route path="/" element={<MainPage />} />
        <Route path="/places/register" element={<RegisterPlacePage />} />
      </Routes>
    </PlacesProvider>
  )
}

export default App
