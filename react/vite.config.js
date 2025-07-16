import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig({
    plugins: [
        react(),
        tailwindcss()
    ],
    server: {
        proxy: {
            '/api': {
                target: 'http://homelab.lan:8080', // Your Java server URL
                changeOrigin: true,
                secure: false,
                // Optional: rewrite the path if needed
                // rewrite: (path) => path.replace(/^\/api/, '')
            }
        }
    }
})
