import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The dev server proxies /api to the Spring Boot API (port 18080 by default, see docs/LOCAL_SETUP.md).
const api = process.env.CONNECTONE_API ?? 'http://localhost:18080';

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': { target: api, changeOrigin: true } } },
  test: { environment: 'node' },
});
