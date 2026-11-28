import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발: npm run dev (http://localhost:5173, /api는 백엔드 8080으로 전달)
// 빌드: ../build/frontend → Gradle bootJar가 jar의 static/에 넣는다
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../build/frontend', emptyOutDir: true },
  server: { proxy: { '/api': 'http://localhost:8080' } },
})
