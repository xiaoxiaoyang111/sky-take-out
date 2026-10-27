<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {Search,Camera} from 'lucide-vue-next'
import {api} from '../api'
import EquipmentCard from '../components/EquipmentCard.vue'
import {validateRentalDates} from '../rental-dates'
const categories=ref([]),records=ref([]),total=ref(0),page=ref(1),keyword=ref(''),categoryId=ref(''),startDate=ref(''),endDate=ref(''),dates=ref(null),loading=ref(true),error=ref('')
let generation=0
async function load(reset=false){
 if(reset)page.value=1
 const id=++generation;loading.value=true;error.value=''
 try{
  if(startDate.value||endDate.value)validateRentalDates(startDate.value,endDate.value)
  const selected=startDate.value?{startDate:startDate.value,endDate:endDate.value}:null
  const data=await api('/equipment',{query:{page:page.value,pageSize:9,categoryId:categoryId.value,keyword:keyword.value.trim()}})
  const items=await Promise.all(data.records.map(async item=>{
   if(!selected)return item
   try{const q=await api('/equipment/'+item.id+'/availability',{query:selected});return {...item,availableCount:q.availableCount}}
   catch{return {...item,availabilityError:true}}
  }))
  if(id===generation){records.value=items;total.value=data.total;dates.value=selected}
 }catch(e){if(id===generation)error.value=e.message}finally{if(id===generation)loading.value=false}
}
onMounted(async()=>{load();try{categories.value=await api('/categories')}catch(e){error.value=e.message}})
onBeforeUnmount(()=>generation++)
function choose(id){categoryId.value=id;load(true)}
</script>
<template><section class="catalog-page wrap">
<header class="catalog-heading"><div><p class="section-kicker">光屿 · 摄影器材租赁</p><h1>这次拍摄，需要什么？</h1><p class="muted">相机、镜头、灯光与配件。选好器材和租期，预约后到店领取。</p></div><RouterLink to="/guide" class="plain-link">第一次租？查看租赁须知 →</RouterLink></header>
<form class="catalog-filter" @submit.prevent="load(true)"><label class="keyword-field">器材 / 品牌<div class="search-input"><Search :size="18"/><input v-model="keyword" placeholder="搜索型号或品牌" aria-label="搜索器材" maxlength="100"></div></label><label>领取日期<input v-model="startDate" type="date"></label><label>归还日期<input v-model="endDate" type="date"></label><button class="button" :disabled="loading">{{loading?'查询中…':'查询器材'}}</button></form>
<div class="catalog-layout"><aside class="catalog-sidebar"><h2>器材分类</h2><div class="category-nav"><button :class="{active:categoryId===''}" @click="choose('')">全部器材</button><button v-for="c in categories" :key="c.id" :class="{active:categoryId===c.id}" @click="choose(c.id)">{{c.name}}</button></div><div class="sidebar-note"><h3>租赁前了解</h3><p>按日计费，归还日不计费。</p><p>租金及押金到店办理。</p><p>一件实物一个编号，领取和归还时现场核验。</p><RouterLink to="/guide" class="plain-link">查看完整须知 →</RouterLink></div></aside>
<div class="catalog-results"><div class="results-heading"><h2>{{categories.find(c=>c.id===categoryId)?.name||'全部器材'}} <span>{{total}} 个型号</span></h2><span class="muted">{{dates?dates.startDate+' — '+dates.endDate:'选择日期后可查看可租数量'}}</span></div>
<div v-if="error" class="state error" role="alert"><p>{{error}}</p><button class="button small" @click="load()">重新查询</button></div><div v-else-if="loading" class="product-grid"><div v-for="i in 6" :key="i" class="skeleton-card"/></div><div v-else-if="!records.length" class="state panel"><Camera :size="32"/><h3>没有找到符合条件的器材</h3><p>试试其他型号、品牌或分类。</p></div><div v-else class="product-grid"><EquipmentCard v-for="item in records" :key="item.id" :item="item" :dates="dates"/></div>
<p class="catalog-footnote">图片仅作器材展示，具体配置以门店交接清单为准。报价和库存以提交预约时的校验为准。</p><div v-if="total>9" class="pagination"><button :disabled="page===1||loading" @click="page--;load()">上一页</button><span>{{page}} / {{Math.ceil(total/9)}}</span><button :disabled="page*9>=total||loading" @click="page++;load()">下一页</button></div></div></div></section></template>
