<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'

import {Heart,ClipboardList,Star,ChevronRight} from 'lucide-vue-next'
import AccountAvatar from '../components/AccountAvatar.vue'
import {auth,api,saveSession} from '../api'
const profile=ref(null),summary=ref(null),loading=ref(false),error=ref('')
let active=true
onBeforeUnmount(()=>{active=false})
async function load(){if(!auth.user)return;const uid=auth.user.user.id;loading.value=true;error.value='';try{const [p,s]=await Promise.all([api('/me'),api('/member/summary')]);if(!active||auth.user?.user?.id!==uid)return;profile.value=p;summary.value=s;saveSession({...auth.user,user:{...auth.user.user,name:p.name,avatarUrl:p.avatarUrl}})}catch(e){if(active)error.value=e.message}finally{if(active)loading.value=false}}
onMounted(load)
</script>
<template><section class="wrap account-page" aria-label="个人中心"><header class="account-toolbar">
<RouterLink class="avatar-entry" :to="auth.user?'/me/profile':{path:'/login',query:{redirect:'/me/profile'}}" :aria-label="auth.user?'查看个人信息':'登录 / 注册'" :title="auth.user?'个人信息':'登录 / 注册'"><AccountAvatar :src="profile?.avatarUrl||auth.user?.user?.avatarUrl" :name="profile?.name||auth.user?.user?.name"/></RouterLink>
</header>
<p v-if="error" role="alert" class="alert error">{{error}}<button class="text-button" @click="load">重试</button></p>
<section class="transactions-section"><h2>我的交易</h2><div class="transaction-tiles"><RouterLink to="/favorites"><Heart :size="27"/><strong>我的收藏</strong><span>{{!auth.user?'登录后查看':loading?'读取中':summary?summary.favorites+' 件':'暂不可用'}}</span></RouterLink><RouterLink to="/orders"><ClipboardList :size="27"/><strong>全部订单</strong><span>{{!auth.user?'登录后查看':loading?'读取中':summary?summary.orders+' 笔':'暂不可用'}}</span></RouterLink><RouterLink to="/reviews"><Star :size="27"/><strong>历史评价</strong><span>{{!auth.user?'登录后查看':loading?'读取中':summary?summary.reviews+' 条':'暂不可用'}}</span></RouterLink></div></section>
<section class="account-links panel"><RouterLink to="/guide"><span>租赁须知</span><ChevronRight :size="18"/></RouterLink><RouterLink to="/assistant"><span>找小影聊聊</span><ChevronRight :size="18"/></RouterLink></section><p class="account-footnote">龙院摄影器材 · 到店领取与归还<br>租金、押金在线下办理，平台暂不提供线上支付。</p></section></template>
