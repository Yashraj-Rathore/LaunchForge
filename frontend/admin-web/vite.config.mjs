import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        xfwd: true,
      },
      '/login': {
        target: 'http://127.0.0.1:8080',
        xfwd: true,
      },
      '/oauth2': {
        target: 'http://127.0.0.1:8080',
        xfwd: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});
