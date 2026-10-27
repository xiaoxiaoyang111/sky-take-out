<script setup>
import {ref,watch,nextTick,onBeforeUnmount} from 'vue'
import {useRoute} from 'vue-router'
import {MessageCircle,X,Send} from 'lucide-vue-next'
import {api,auth} from '../api'
import {assistant} from '../assistant'
const route=useRoute(),turns=ref([]),text=ref(''),error=ref(''),busy=ref(false),modelConfigured=ref(false),consent=ref(false),historyBusy=ref(false),input=ref(null),log=ref(null)
let sessionId='',generation=0,controller=null
function cancel(){generation++;controller?.abort();busy.value=false;historyBusy.value=false}
async function bottom(){await nextTick();if(log.value)log.value.scrollTop=log.value.scrollHeight}
async function load(){
 cancel();error.value='';turns.value=[]
 if(!assistant.open)return
 const current=generation;controller=new AbortController();historyBusy.value=true
 try{
  const state=await api('/assistant/status',{signal:controller.signal});if(current!==generation)return;modelConfigured.value=state.modelConfigured
  if(auth.user){
   const key='gearhub_chat_'+auth.user.user.id
   sessionId=sessionStorage.getItem(key)||(globalThis.crypto?.randomUUID?.()||('chat-'+Date.now()+'-'+Math.random().toString(36).slice(2)));sessionStorage.setItem(key,sessionId)
   const list=await api('/assistant/history',{query:{sessionId},signal:controller.signal});if(current===generation)turns.value=list
  }
 }catch(e){if(current===generation&&e.name!=='AbortError')error.value=e.message}
 finally{if(current===generation){historyBusy.value=false;bottom();input.value?.focus()}}
}
watch(()=>[assistant.open,auth.user?.user?.id],load)
watch(()=>auth.user?.user?.id,()=>{consent.value=false})
watch(()=>route.path,path=>{if(path.startsWith('/admin'))assistant.open=false})
onBeforeUnmount(cancel)
async function send(action='ASK'){
 if(busy.value||historyBusy.value||!auth.user)return
 if(action==='ASK'&&!text.value.trim())return
 if(action==='ASK'&&modelConfigured.value&&!consent.value){error.value='使用模型咨询前，请确认下方资料发送说明；也可直接使用规则和业务查询按钮。';return}
 const context=assistant.context||{}
 const question=action==='RULES'?'查看租赁规则':action==='ORDERS'?'查询本人最近订单':action==='QUOTE'?'查询这款器材的库存与报价':text.value.trim()
 const current=++generation;controller=new AbortController();busy.value=true;error.value=''
 try{
  const result=await api('/assistant/messages',{method:'POST',body:{sessionId,message:question,action,allowModel:consent.value,equipmentId:context.equipmentId,startDate:context.startDate,endDate:context.endDate},signal:controller.signal})
  if(current===generation){turns.value=[...turns.value,result].slice(-10);if(action==='ASK')text.value='';bottom()}
 }catch(e){if(current===generation&&e.name!=='AbortError')error.value=e.message}
 finally{if(current===generation)busy.value=false}
}
async function clear(){
 if(busy.value||historyBusy.value||!auth.user)return
 const current=++generation;controller=new AbortController();busy.value=true;error.value=''
 try{await api('/assistant/history',{method:'DELETE',query:{sessionId},signal:controller.signal});if(current===generation)turns.value=[]}
 catch(e){if(current===generation&&e.name!=='AbortError')error.value=e.message}
 finally{if(current===generation)busy.value=false}
}
function close(){assistant.open=false;cancel()}
</script>
<template>
 <button v-if="!route.path.startsWith('/admin')&&!assistant.open" class="assistant-launch" @click="assistant.open=true"><MessageCircle :size="18"/>租赁助手</button>
 <section v-if="assistant.open&&!route.path.startsWith('/admin')" class="assistant-panel" role="dialog" aria-label="租赁助手" @keydown.esc="close">
  <header><div><h2>租赁助手</h2><p>{{modelConfigured?'规则查询 · 实时业务 · 模型咨询':'规则与业务查询可用 · 模型尚未配置'}}</p></div><button class="icon-button" aria-label="关闭助手" @click="close"><X :size="20"/></button></header>
  <p v-if="assistant.context" class="assistant-context">当前器材：{{assistant.context.name}}<br>{{assistant.context.startDate}} 至 {{assistant.context.endDate}} <button class="text-button" :disabled="busy" @click="assistant.context=null">取消关联</button></p>
  <div v-if="!auth.user" class="assistant-welcome"><MessageCircle :size="26"/><h3>有什么租赁问题？</h3><p>登录后可查询规则、所选租期报价和本人订单。</p><RouterLink class="button" :to="{path:'/login',query:{redirect:route.fullPath}}" @click="close">登录后咨询</RouterLink><RouterLink to="/guide" class="plain-link" @click="close">先阅读租赁须知</RouterLink></div>
  <template v-else>
   <div class="assistant-quick"><button :disabled="busy||historyBusy" @click="send('RULES')">租赁规则</button><button :disabled="busy||historyBusy" @click="send('ORDERS')">本人订单</button><button v-if="assistant.context?.startDate&&assistant.context?.endDate" :disabled="busy||historyBusy" @click="send('QUOTE')">这款器材的报价</button></div>
   <div ref="log" class="assistant-log" aria-live="polite" aria-relevant="additions text"><p v-if="historyBusy" class="muted">正在读取会话…</p><p v-else-if="!turns.length" class="muted">可先选择上方问题。实时业务由系统查询，模型不执行预约或取消操作。</p><article v-for="(turn,index) in turns" :key="index"><p class="chat-question">{{turn.question}}</p><div class="chat-answer"><small>{{({RULES:'平台租赁规则',LIVE:'系统实时查询',MODEL:'模型建议，请核对资料'})[turn.mode]}}</small><p>{{turn.answer}}</p><RouterLink v-for="source in turn.sources" :key="source" :to="source" @click="close">{{source==='/guide'?'查看租赁须知':source==='/orders'?'前往我的租赁':'查看器材详情'}} →</RouterLink></div></article><p v-if="busy" class="muted">正在处理，请稍候…</p></div>
   <p v-if="error" role="alert" class="assistant-error">{{error}}</p>
   <form class="assistant-form" @submit.prevent="send()"><label v-if="modelConfigured" class="assistant-consent"><input type="checkbox" v-model="consent">同意将咨询内容和当前器材资料发送给配置的模型服务。订单查询不发送给模型。</label><label class="sr-only" for="assistant-message">咨询内容</label><textarea id="assistant-message" ref="input" v-model="text" maxlength="600" :disabled="busy||historyBusy" :placeholder="modelConfigured?'描述你的租赁问题':'可询问租期、押金等规则；自由咨询待模型配置后启用'"/><div><button type="button" class="text-button" :disabled="busy||historyBusy||!turns.length" @click="clear">清除会话</button><span class="muted">30分钟无新消息后过期</span><button class="button small" :disabled="busy||historyBusy||!text.trim()"><Send :size="15"/>发送</button></div><small>请勿发送密码、证件号码等敏感信息。</small></form>
  </template>
 </section>
</template>
