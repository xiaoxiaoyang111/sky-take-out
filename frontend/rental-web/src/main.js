import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Catalog from './pages/Catalog.vue'
import Detail from './pages/Detail.vue'
import Auth from './pages/Auth.vue'
import Orders from './pages/Orders.vue'
import Dashboard from './pages/Dashboard.vue'
import Equipment from './pages/Equipment.vue'
import { session } from './api'
import './style.css'
import './comfort.css'
import Guide from './pages/Guide.vue'

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', component: Catalog }, { path: '/equipment/:id', component: Detail }, { path: '/guide', component: Guide },
  { path: '/login', component: Auth }, { path: '/orders', component: Orders, meta: { role: 'USER' } },
  { path: '/admin/login', component: Auth }, { path: '/admin', component: Dashboard, meta: { role: 'ADMIN' } },
  { path: '/admin/equipment', component: Equipment, meta: { role: 'ADMIN' } },
  { path: '/admin/orders', component: Orders, meta: { role: 'ADMIN' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
] })
router.beforeEach(to => {
  if (to.meta.role && !session(to.meta.role)) return { path: to.meta.role === 'ADMIN' ? '/admin/login' : '/login', query: { redirect: to.fullPath } }
})
createApp(App).use(router).mount('#app')
