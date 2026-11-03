// Only backend-verified names get router links; model HTML and Markdown remain plain text.
export function equipmentSegments(answer, references) {
  const text=typeof answer==='string'?answer:''
  const names=new Map(),ambiguous=new Set()
  for(const item of Array.isArray(references)?references:[]) {
    const id=Number(item?.id),name=typeof item?.name==='string'?item.name:''
    if(!Number.isSafeInteger(id)||id<=0||!name.trim())continue
    if(names.has(name)&&names.get(name)!==id)ambiguous.add(name)
    else names.set(name,id)
  }
  const candidates=[...names].filter(([name])=>!ambiguous.has(name)).sort((a,b)=>b[0].length-a[0].length)
  const segments=[]
  let cursor=0
  while(cursor<text.length) {
    let found=null,position=text.length
    for(const [name,id] of candidates) {
      const index=text.indexOf(name,cursor)
      if(index>=0&&index<position){found={name,id};position=index}
    }
    if(!found){segments.push({text:text.slice(cursor)});break}
    let prefix=text.slice(cursor,position),next=position+found.name.length
    for(const marker of ['**','__']) {
      if(prefix.endsWith(marker)&&text.startsWith(marker,next)){prefix=prefix.slice(0,-marker.length);next+=marker.length;break}
    }
    if(prefix)segments.push({text:prefix})
    segments.push({text:found.name,to:'/equipment/'+found.id})
    cursor=next
  }
  return segments
}
