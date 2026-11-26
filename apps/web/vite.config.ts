import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  // Monorepo: mobil uygulama farklı React sürümü kullandığı için web kendi kopyasını kullanmalı.
  resolve: { dedupe: ['react', 'react-dom'] },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:3000' },
  },
})
