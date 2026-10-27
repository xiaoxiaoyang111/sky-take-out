<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Aperture, ArrowUpRight, LogOut, LayoutDashboard, Camera, ClipboardList, Menu, X } from 'lucide-vue-next'
import { ref } from 'vue'
import { auth, logout } from './api'
import RentalAssistant from './components/RentalAssistant.vue'
const route = useRoute(), router = useRouter(), menu = ref(false)
const admin = computed(() => route.path.startsWith('/admin'))
function leave() { logout(admin.value ? 'ADMIN' : 'USER'); router.push(admin.value ? '/admin/login' : '/'); menu.value = false }
</script>
<template>
  <div :class="['app', { 'admin-app': admin }]">
    <header class="topbar"><RouterLink class="brand" :to="admin ? '/admin' : '/'" @click="menu=false"><span class="brand-mark"><Aperture :size="27"/></span><span>光屿 <b>GearHub</b><small>{{ admin ? '门店管理工作台' : '摄影器材租赁' }}</small></span></RouterLink>
      <nav :class="{ open: menu }">
        <template v-if="admin"><RouterLink to="/admin" @click="menu=false"><LayoutDashboard :size="16"/>概览</RouterLink><RouterLink to="/admin/equipment" @click="menu=false"><Camera :size="16"/>器材与实物</RouterLink><RouterLink to="/admin/orders" @click="menu=false"><ClipboardList :size="16"/>租赁订单</RouterLink><RouterLink to="/" @click="menu=false">用户端 <ArrowUpRight :size="15"/></RouterLink></template>
        <template v-else><RouterLink to="/" @click="menu=false">器材目录</RouterLink><RouterLink to="/guide" @click="menu=false">租赁须知</RouterLink><RouterLink to="/orders" @click="menu=false">我的租赁</RouterLink><RouterLink to="/admin" @click="menu=false">门店管理 <ArrowUpRight :size="15"/></RouterLink></template>
      </nav>
      <div class="account"><template v-if="admin ? auth.admin : auth.user"><span>{{ (admin ? auth.admin : auth.user).user?.name }}</span><button class="icon-button" title="退出登录" @click="leave"><LogOut :size="18"/></button></template><RouterLink v-else :to="admin ? '/admin/login' : '/login'" class="button small">{{ admin ? '员工登录' : '登录 / 注册' }}<ArrowUpRight :size="15"/></RouterLink><button class="icon-button mobile-menu" aria-label="展开导航" @click="menu=!menu"><X v-if="menu"/><Menu v-else/></button></div>
    </header>
    <main><RouterView :key="route.path"/></main>
    <RentalAssistant/>
    <footer class="footer"><div><Aperture :size="18"/><strong>光屿 GearHub</strong><span>器材租赁与门店交接</span></div><p>单门店 · 到店领取 / 归还 · 线下办理租金与押金</p><small>© {{ new Date().getFullYear() }} GearHub · 器材租赁平台</small></footer>
  </div>
</template>
