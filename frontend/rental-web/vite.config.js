import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
const target = process.env.RENTAL_API_TARGET || 'http://127.0.0.1:8080'
export default defineConfig({ plugins: [vue()], server: { host: '0.0.0.0', port: 5173, strictPort: true, proxy: { '/rental': target, '/images': target } } })
