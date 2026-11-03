
import { createRouter, createWebHistory } from 'vue-router'

import Catalog from './pages/Catalog.vue'
import Detail from './pages/Detail.vue'
import Auth from './pages/Auth.vue'
import Orders from './pages/Orders.vue'
import Dashboard from './pages/Dashboard.vue'
import Equipment from './pages/Equipment.vue'
import { session } from './api'
import Guide from './pages/Guide.vue'
import MyAccount from './pages/MyAccount.vue'
import Profile from './pages/Profile.vue'
import Favorites from './pages/Favorites.vue'
import Reviews from './pages/Reviews.vue'
import Xiaoying from './pages/Xiaoying.vue'

export function createRentalRouter(history = createWebHistory()) {
const router = createRouter({ history, routes: [
  { path: '/', component: Catalog }, { path: '/equipment/:id', component: Detail }, { path: '/guide', component: Guide },
  { path: '/assistant', component: Xiaoying }, { path: '/me', component: MyAccount },
  { path: '/me/profile', component: Profile, meta: { role: 'USER' } },
  { path: '/favorites', component: Favorites, meta: { role: 'USER' } }, { path: '/reviews', component: Reviews, meta: { role: 'USER' } },
  { path: '/login', component: Auth }, { path: '/orders', component: Orders, meta: { role: 'USER' } },
  { path: '/admin/login', component: Auth }, { path: '/admin', component: Dashboard, meta: { role: 'ADMIN' } },
  { path: '/admin/equipment', component: Equipment, meta: { role: 'ADMIN' } },
  { path: '/admin/orders', component: Orders, meta: { role: 'ADMIN' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
] })
router.beforeEach(to => {
  if (to.meta.role && !session(to.meta.role)) return { path: to.meta.role === 'ADMIN' ? '/admin/login' : '/login', query: { redirect: to.fullPath } }
})
return router
}
