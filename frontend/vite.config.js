import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({plugins:[vue()],build:{outDir:'../src/main/resources/public',emptyOutDir:true},server:{proxy:{'/api':'http://localhost:8080'}}})
