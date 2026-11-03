<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {useRouter} from 'vue-router'
import {ArrowLeft,ChevronRight} from 'lucide-vue-next'
import AccountAvatar from '../components/AccountAvatar.vue'
import {api,auth,logout,saveSession} from '../api'

const router=useRouter(),profile=ref(null),loading=ref(false),editing=ref(false),name=ref(''),saving=ref(false),error=ref(''),success=ref('')
const avatarInput=ref(null),uploading=ref(false)
let active=true,controller=null
onBeforeUnmount(()=>{active=false;controller?.abort()})
async function load(){
 const uid=auth.user?.user?.id;if(!uid)return
 controller?.abort();controller=new AbortController();loading.value=true;error.value=''
 try{const p=await api('/me',{signal:controller.signal});if(!active||auth.user?.user?.id!==uid)return;profile.value=p;name.value=p.name;saveSession({...auth.user,user:{...auth.user.user,name:p.name,avatarUrl:p.avatarUrl}})}
 catch(e){if(active&&e.name!=='AbortError')error.value=e.message}
 finally{if(active)loading.value=false}
}
function edit(){name.value=profile.value.name;editing.value=true;success.value='';error.value=''}
async function save(){
 if(saving.value||uploading.value)return
 const value=name.value.trim();if(!value||value.length>32){error.value='昵称须为1至32个字符';return}
 const uid=auth.user?.user?.id;if(!uid)return
 saving.value=true;error.value='';success.value='';controller=new AbortController()
 try{const p=await api('/member/profile',{method:'PUT',body:{name:value},signal:controller.signal});if(!active||auth.user?.user?.id!==uid)return;profile.value=p;saveSession({...auth.user,user:{...auth.user.user,name:p.name,avatarUrl:p.avatarUrl}});editing.value=false;success.value='昵称已更新'}
 catch(e){if(active&&e.name!=='AbortError')error.value=e.message}
 finally{if(active)saving.value=false}
}
async function uploadAvatar(event){
 const file=event.target.files?.[0];event.target.value='';if(!file||saving.value||uploading.value)return
 error.value='';success.value=''
 if(file.size>2*1024*1024||!file.size){error.value='头像须为不超过2MB的图片';return}
 if(file.type&&!['image/png','image/jpeg'].includes(file.type)){error.value='请选择PNG或JPG图片';return}
 const uid=auth.user?.user?.id;if(!uid)return
 const body=new FormData();body.append('file',file);uploading.value=true;controller=new AbortController()
 try{const result=await api('/member/avatar',{method:'POST',body,signal:controller.signal});if(!active||auth.user?.user?.id!==uid)return;profile.value={...profile.value,avatarUrl:result.avatarUrl};saveSession({...auth.user,user:{...auth.user.user,avatarUrl:result.avatarUrl}});success.value='头像已更新'}
 catch(e){if(active&&e.name!=='AbortError')error.value=e.message}
 finally{if(active)uploading.value=false}
}
function leave(switchAccount){if(saving.value||uploading.value)return;logout('USER');router.replace(switchAccount?{path:'/login',query:{redirect:'/me/profile'}}:'/me')}
onMounted(load)
</script>
<template>
 <section class="wrap profile-page" aria-label="个人信息管理">
  <header class="profile-page-heading"><RouterLink to="/me" class="icon-button" aria-label="返回我的"><ArrowLeft :size="21"/></RouterLink><h1>个人信息</h1></header>
  <p v-if="loading" class="muted" role="status">正在读取个人信息…</p>
  <p v-if="error" class="alert error" role="alert">{{error}}<button v-if="!profile" class="text-button" @click="load">重试</button></p>
  <p v-if="success" class="alert success" role="status">{{success}}</p>
  <div class="profile-settings panel">
   <button class="profile-setting-row editable-row" aria-label="上传或更换头像" :disabled="loading||saving||uploading||!profile" @click="avatarInput?.click()"><span>{{uploading?'头像上传中…':'头像'}}</span><span class="profile-row-value"><AccountAvatar :src="profile?.avatarUrl||auth.user?.user?.avatarUrl" :name="profile?.name||auth.user?.user?.name"/><ChevronRight :size="17"/></span></button>
   <input ref="avatarInput" class="avatar-file-input" type="file" accept="image/png,image/jpeg" aria-label="选择头像图片" @change="uploadAvatar">
   <button class="profile-setting-row editable-row" :disabled="loading||saving||uploading||!profile" :aria-expanded="editing" aria-controls="nickname-editor" @click="edit"><span>昵称</span><span class="profile-row-value">{{profile?.name||auth.user?.user?.name}}<ChevronRight :size="17"/></span></button>
   <form v-if="editing" id="nickname-editor" class="nickname-editor" @submit.prevent="save"><label for="profile-name">修改昵称</label><input id="profile-name" v-model="name" required maxlength="32" autocomplete="nickname" :disabled="saving||uploading"><div class="action-row"><button class="button small" :disabled="saving||uploading">{{saving?'保存中…':'保存'}}</button><button type="button" class="button secondary small" :disabled="saving||uploading" @click="editing=false;error=''">取消</button></div></form>
   <div class="profile-setting-row"><span>登录账号</span><span class="profile-row-value">{{profile?.username||auth.user?.user?.username}}</span></div>
   <div class="profile-setting-row"><span>账号类型</span><span class="profile-row-value">普通客户</span></div>
  </div>
  <p class="profile-settings-note">点击头像行上传图片，支持PNG、JPG，最大2MB。登录账号暂不支持修改。</p>
  <div class="profile-settings profile-settings-actions panel"><button class="profile-setting-row" :disabled="saving||uploading" @click="leave(true)"><span>切换账号</span><ChevronRight :size="17"/></button><button class="profile-setting-row profile-logout" :disabled="saving||uploading" @click="leave(false)"><span>退出登录</span><ChevronRight :size="17"/></button></div>
 </section>
</template>
