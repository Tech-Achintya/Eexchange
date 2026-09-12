import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      
      '/auth': 'https://backend-eexchange.onrender.com', 
      '/items': 'https://backend-eexchange.onrender.com',
      '/user': 'https://backend-eexchange.onrender.com',
    },
  },
})
