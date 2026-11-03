<script setup>
import {ref,watch} from 'vue'
import {UserRound} from 'lucide-vue-next'
const props=defineProps({src:String,name:String})
const failed=ref(false)
watch(()=>props.src,()=>{failed.value=false})
</script>
<template><span class="profile-avatar"><img v-if="src&&!failed" :src="src" alt="头像" @error="failed=true"><span v-else-if="name">{{Array.from(name)[0]}}</span><UserRound v-else :size="22"/></span></template>
