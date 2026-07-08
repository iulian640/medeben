import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import { setOnUnauthorized } from './services/api'
import { useAuthStore } from './stores/auth'

const pinia = createPinia()

createApp(App).use(pinia).use(router).mount('#app')

// Un 401 en una petición autenticada = la sesión ya no vale: se limpia y se
// vuelve al login con la vuelta preparada. Sin bucles: el login hace sus
// peticiones sin token (nunca dispara esto) y aquí no se re-navega si ya
// estamos en la pantalla de login.
setOnUnauthorized(() => {
  const auth = useAuthStore(pinia)
  auth.sesionCaducada()
  const actual = router.currentRoute.value
  if (actual.name !== 'login') {
    router.push({ name: 'login', query: { redirect: actual.fullPath } })
  }
})
