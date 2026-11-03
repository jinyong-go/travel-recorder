import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 사용자 웹(5173)과 함께 띄울 수 있도록 포트를 나눈다.
export default defineConfig({
  plugins: [react()],
  server: { port: 5174, strictPort: true },
  preview: { port: 5174, strictPort: true },
})
