<script setup>
import {computed,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {Camera,MessageCircle,UserRound,LayoutDashboard,ClipboardList,Menu,X,LogOut} from 'lucide-vue-next'
import {auth,logout} from './api'
const route=useRoute(),router=useRouter(),menu=ref(false)
const admin=computed(()=>route.path.startsWith('/admin'))
const section=computed(()=>route.path==='/assistant'?'assistant':/^\/(me|orders|favorites|reviews|login)/.test(route.path)?'me':'home')
function leave(){logout('ADMIN');menu.value=false;router.push('/admin/login')}
</script>
<template><div :class="['app',{'admin-app':admin,'member-app':!admin}]">
<header class="topbar"><RouterLink class="brand" :to="admin?'/admin':'/'" @click="menu=false"><span class="brand-mark"><Camera :size="23"/></span><span>龙院摄影器材<small>{{admin?'门店管理工作台':'摄影 · 从这里开始'}}</small></span></RouterLink>
<template v-if="admin"><nav :class="{open:menu}"><RouterLink to="/admin" @click="menu=false"><LayoutDashboard :size="16"/>概览</RouterLink><RouterLink to="/admin/equipment" @click="menu=false"><Camera :size="16"/>器材与实物</RouterLink><RouterLink to="/admin/orders" @click="menu=false"><ClipboardList :size="16"/>租赁订单</RouterLink><RouterLink to="/">用户端</RouterLink></nav><div class="account"><template v-if="auth.admin"><span>{{auth.admin.user?.name}}</span><button class="icon-button" title="退出登录" @click="leave"><LogOut :size="18"/></button></template><RouterLink v-else to="/admin/login" class="button small">员工登录</RouterLink><button class="icon-button mobile-menu" aria-label="展开导航" @click="menu=!menu"><X v-if="menu"/><Menu v-else/></button></div></template>
<div v-else class="member-header-links"><RouterLink to="/guide">租赁须知</RouterLink><RouterLink class="staff-link" to="/admin">门店管理</RouterLink></div></header>
<main><RouterView :key="route.path"/></main>
<nav v-if="!admin" class="bottom-navigation" aria-label="用户主导航"><RouterLink to="/" :class="{active:section==='home'}"><Camera :size="23"/><span>推荐</span></RouterLink><RouterLink to="/assistant" :class="['xiaoying-tab',{active:section==='assistant'}]"><span class="xiaoying-symbol"><MessageCircle :size="25"/></span><span>小影</span></RouterLink><RouterLink to="/me" :class="{active:section==='me'}"><UserRound :size="23"/><span>我的</span></RouterLink></nav>
<footer v-if="admin" class="footer"><strong>龙院摄影器材</strong><p>到店领取 / 归还 · 线下办理租金与押金</p></footer>
</div></template>
