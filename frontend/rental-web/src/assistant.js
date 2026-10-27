import {reactive} from 'vue'
export const assistant=reactive({open:false,context:null})
export function consultEquipment(item,startDate,endDate){assistant.context={equipmentId:item.id,name:item.name,startDate,endDate};assistant.open=true}
