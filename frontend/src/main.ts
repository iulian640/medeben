import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import { crearManejador401 } from './router/sesion401'
import { setOnUnauthorized } from './services/api'
import { registrarPWA } from './pwa'

const pinia = createPinia()

createApp(App).use(pinia).use(router).mount('#app')

// Un 401 en una petición autenticada = sesión caducada (ver sesion401.ts).
setOnUnauthorized(crearManejador401(router, pinia))

// Service worker con recarga al actualizar y comprobación periódica (ver pwa.ts).
registrarPWA()
