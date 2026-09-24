import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.jsx'
import { applyTheme, getInitialTheme } from './theme/themes.js'

// 저장된 테마를 첫 화면을 그리기 전에 적용한다. 어느 화면으로 들어오든 같아야 하고,
// 그리고 나서 바꾸면 한 번 깜빡인다.
applyTheme(getInitialTheme())

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
