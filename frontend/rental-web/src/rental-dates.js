import {today} from './api.js'

export function validateRentalDates(start,end,current=today()) {
  const day=value=>{
    if(!/^\d{4}-\d{2}-\d{2}$/.test(value||''))return NaN
    const result=new Date(value+'T00:00:00Z')
    return Number.isNaN(+result)||result.toISOString().slice(0,10)!==value?NaN:+result
  }
  const a=day(start),b=day(end),now=day(current),days=(b-a)/86400000
  if(!Number.isFinite(a)||!Number.isFinite(b))throw new Error('请填写有效的领取和归还日期')
  if(days<1||days>30)throw new Error('归还日期须晚于领取日期，租期为1至30天')
  if(a<now||a>now+180*86400000)throw new Error('领取日期须在今天至未来180天内')
  return days
}
