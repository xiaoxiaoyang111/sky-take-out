// Bounded local HTTP acceptance + benchmark. Own fixtures only; never log credentials.
import {readFileSync,existsSync,mkdirSync,writeFileSync,unlinkSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {resolve,dirname,join} from 'node:path'
import {fileURLToPath} from 'node:url'
import {randomUUID,createHash} from 'node:crypto'
import {deflateSync} from 'node:zlib'
import {performance} from 'node:perf_hooks'
import {homedir,cpus,platform,release} from 'node:os'
import assert from 'node:assert/strict'

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),base=process.env.RENTAL_BASE_URL||'http://127.0.0.1:8082'
assert.ok(['localhost','127.0.0.1','[::1]'].includes(new URL(base).hostname),'This bounded verification runner only targets a local instance')
const localPath=join(root,'.rental/application-local.yml'),local=existsSync(localPath)?readFileSync(localPath,'utf8'):''
function setting(env,key,fallback){return process.env[env]||local.match(new RegExp('^\\s+'+key+':\\s*(.+?)\\s*$','m'))?.[1]?.replace(/^["']|["']$/g,'')||fallback}
const mysql=process.env.MYSQL_CLI||(existsSync('C:/mysql/mysql-8.0.46-winx64/bin/mysql.exe')?'C:/mysql/mysql-8.0.46-winx64/bin/mysql.exe':'mysql')
const db={host:setting('SKY_DB_HOST','host','localhost'),port:setting('SKY_DB_PORT','port','3306'),user:setting('SKY_DB_USERNAME','username','root'),database:setting('SKY_DB_DATABASE','database','sky_take_out'),password:setting('SKY_DB_PASSWORD','password','')}
function sql(query){return execFileSync(mysql,[`--host=${db.host}`,`--port=${db.port}`,`--user=${db.user}`,`--database=${db.database}`,'--default-character-set=utf8mb4','--batch','--skip-column-names','--execute',query],{env:{...process.env,MYSQL_PWD:db.password},encoding:'utf8',stdio:['pipe','pipe','pipe']}).trim()}
async function request(path,{token,body,method='GET',raw=false}={}){const start=performance.now();const response=await fetch(base+path,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body&&! (body instanceof FormData)?{'Content-Type':'application/json'}:{})},body:body===undefined?undefined:body instanceof FormData?body:JSON.stringify(body),signal:AbortSignal.timeout(55000)});return {status:response.status,value:raw?new Uint8Array(await response.arrayBuffer()):await response.json(),ms:performance.now()-start}}
async function api(path,options){const result=await request('/rental'+path,options);assert.equal(result.status,200,`HTTP status: ${path}`);assert.equal(result.value.code,1,`Business result: ${path}: ${result.value.msg}`);return result.value.data}
const prefix='DEMO'+randomUUID().replaceAll('-','').slice(0,12),password=randomUUID(),fixtures={customers:[],files:[]},checks=[]
let admin,category,equipment,sessionId=randomUUID(),userToken
const report={date:new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'}),base,environment:{platform:platform(),os:release(),cpu:cpus()[0]?.model,logicalCpus:cpus().length,node:process.version},checks}
async function check(name,run){const start=performance.now();await run();checks.push({name,passed:true,elapsedMs:Math.round(performance.now()-start)});console.log('PASS '+name)}
function png(){const crc=buffer=>{let c=0xffffffff;for(const byte of buffer){c^=byte;for(let j=0;j<8;j++)c=(c>>>1)^((c&1)?0xedb88320:0)}return (c^0xffffffff)>>>0};const chunk=(type,data)=>{const t=Buffer.from(type),out=Buffer.alloc(12+data.length);out.writeUInt32BE(data.length);t.copy(out,4);data.copy(out,8);out.writeUInt32BE(crc(Buffer.concat([t,data])),8+data.length);return out};const head=Buffer.alloc(13);head.writeUInt32BE(2);head.writeUInt32BE(2,4);head[8]=8;head[9]=6;return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',head),chunk('IDAT',deflateSync(Buffer.alloc(18))),chunk('IEND',Buffer.alloc(0))])}
function date(offset){const value=new Date();value.setUTCDate(value.getUTCDate()+offset);return value.toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'})}
try{
 await check('Deep links and services ready',async()=>{assert.equal((await fetch(base+'/me/profile')).status,200);await api('/categories')})
 const employeePassword=createHash('md5').update(password).digest('hex')
 sql(`insert into employee(name,username,password,phone,sex,id_number,status,create_time,update_time) values('${prefix}','${prefix}','${employeePassword}','','','',1,now(),now())`)
 admin=(await api('/auth/admin-login',{method:'POST',body:{username:prefix,password}})).token
 const user=await api('/auth/register',{method:'POST',body:{username:prefix,password,name:'验收临时客户'}});userToken=user.token;fixtures.customers.push(user.user.id)
 const other=await api('/auth/register',{method:'POST',body:{username:prefix+'b',password,name:'隔离测试客户'}});fixtures.customers.push(other.user.id)
 await check('User role cannot access staff routes',async()=>{assert.equal((await request('/rental/admin/categories',{token:userToken})).status,403);assert.equal((await request('/rental/member/summary')).status,401)})
 await api('/categories') // warm the old catalog generation before mutation
 category=await api('/admin/categories',{method:'POST',token:admin,body:{name:prefix,sort:999,status:1}})
 await check('Catalog cache is invalidated after staff writes',async()=>{assert.ok((await api('/categories')).some(item=>item.id===category.id))})
 equipment=await api('/admin/equipment',{method:'POST',token:admin,body:{categoryId:category.id,name:prefix+'人像器材',brand:'验收专用',description:'运行结束自动清理',image:'/illustrations/camera.svg',dailyRate:25,deposit:200,status:1}})
 await api('/admin/assets',{method:'POST',token:admin,body:{equipmentId:equipment.id,assetCode:prefix+'-asset'}})
 await check('Nickname and avatar persist on the account',async()=>{
  await api('/member/profile',{method:'PUT',token:userToken,body:{name:'头像验收客户'}})
  const body=new FormData();body.append('file',new Blob([png()],{type:'image/png'}),'avatar.png');const upload=await api('/member/avatar',{method:'POST',token:userToken,body});fixtures.files.push(upload.avatarUrl)
  assert.equal((await request(upload.avatarUrl,{raw:true})).status,200);assert.equal((await api('/me',{token:userToken})).avatarUrl,upload.avatarUrl)
  assert.equal((await api('/auth/login',{method:'POST',body:{username:prefix,password}})).user.avatarUrl,upload.avatarUrl)
 })
 await check('Favorites are idempotent and private',async()=>{for(let i=0;i<2;i++)await api('/favorites/'+equipment.id,{method:'PUT',token:userToken});assert.equal((await api('/favorites',{token:userToken})).total,1);assert.equal((await api('/favorites',{token:other.token})).total,0)})
 let order
 await check('Quote, idempotent reservation and ownership',async()=>{
  const quote=await api(`/equipment/${equipment.id}/quote?startDate=${date(0)}&endDate=${date(1)}`);assert.equal(quote.rentalAmount,25);assert.equal(quote.availableCount,1)
  const body={equipmentId:equipment.id,startDate:date(0),endDate:date(1),requestId:randomUUID()};order=await api('/orders',{method:'POST',token:userToken,body});assert.equal((await api('/orders',{method:'POST',token:userToken,body})).id,order.id)
  assert.equal((await request('/rental/orders/'+order.id,{token:other.token})).value.code,0)
 })
 await check('Staff confirm, pickup and return complete the lifecycle',async()=>{for(const [action,status] of [['confirm','CONFIRMED'],['pickup','RENTED'],['return','COMPLETED']]){const result=await api(`/admin/orders/${order.id}/${action}`,{method:'POST',token:admin,body:{note:'验收交接及外观检查完好'}});assert.equal(result.status,status)}assert.equal((await api('/orders/'+order.id,{token:userToken})).status,'COMPLETED')})
 await check('Review requires an owned completed order and is not duplicated',async()=>{const body={orderId:order.id,rating:5,content:'验收测试，自动清理'};await api('/reviews',{method:'POST',token:userToken,body});assert.equal((await request('/rental/reviews',{method:'POST',token:userToken,body})).value.code,0);assert.equal((await api('/reviews',{token:userToken})).total,1)})
 await check('20 competing reservations for one asset produce exactly one winner',async()=>{
  const results=await Promise.all(Array.from({length:20},()=>request('/rental/orders',{method:'POST',token:userToken,body:{equipmentId:equipment.id,startDate:date(1),endDate:date(2),requestId:randomUUID()}})))
  const winners=results.filter(item=>item.status===200&&item.value.code===1);assert.equal(winners.length,1);assert.equal(results.filter(item=>item.status===200&&item.value.code===0).length,19);assert.equal(sql(`select count(*) from rental_order where equipment_id=${equipment.id} and status='PENDING'`),'1');await api('/orders/'+winners[0].value.data.id+'/cancel',{method:'POST',token:userToken,body:{note:'验收完成释放占用'}});report.competition={requests:20,winners:1,businessRejections:19}
 })
 await check('Assistant rules and private history work',async()=>{await api('/assistant/messages',{method:'POST',token:userToken,body:{sessionId,message:'租赁规则',action:'RULES',allowModel:false}});assert.equal((await api('/assistant/history?sessionId='+sessionId,{token:userToken})).length,1)})
 if(process.argv.includes('--model'))await check('Real model answers through the running backend',async()=>{const state=await api('/assistant/status');assert.equal(state.modelConfigured,true);const start=performance.now();const answer=await api('/assistant/messages',{method:'POST',token:userToken,body:{sessionId,message:'你是谁？请简短介绍自己。',action:'ASK',allowModel:true}});assert.equal(answer.mode,'MODEL');assert.ok(answer.answer.includes('小影'));report.realModel={mode:answer.mode,elapsedMs:Math.round(performance.now()-start)}})
 await check('Bounded HTTP catalog benchmark after warmup',async()=>{
  const path='/equipment?page=1&pageSize=12';for(let i=0;i<5;i++)await api(path)
  const samples=[],started=performance.now();let index=0,errors=0
  await Promise.all(Array.from({length:8},async()=>{while(index++<200){const start=performance.now();try{await api(path)}catch{errors++}samples.push(performance.now()-start)}}))
  const seconds=(performance.now()-started)/1000;samples.sort((a,b)=>a-b)
  report.benchmark={endpoint:path,requests:200,concurrency:8,warmup:5,errors,elapsedSeconds:Number(seconds.toFixed(3)),requestsPerSecond:Number((200/seconds).toFixed(2)),p50Ms:Number(samples[Math.ceil(samples.length*.50)-1].toFixed(2)),p95Ms:Number(samples[Math.ceil(samples.length*.95)-1].toFixed(2)),maxMs:Number(samples.at(-1).toFixed(2)),scope:'Local warmed HTTP sample; not a production capacity or improvement claim'};assert.equal(errors,0)
 })
 report.passed=true
}catch(error){report.passed=false;report.failure=String(error.message).replace(/Bearer\s+\S+/g,'Bearer [redacted]');console.error('FAIL '+report.failure);process.exitCode=1}
finally{
 try{
  if(userToken)await api('/assistant/history?sessionId='+sessionId,{method:'DELETE',token:userToken})
  if(category&&admin)await api('/admin/categories/'+category.id,{method:'PUT',token:admin,body:{name:prefix,sort:999,status:0}})
  if(equipment){sql(`delete from rental_order_event where order_id in(select id from rental_order where equipment_id=${equipment.id});delete from rental_order where equipment_id=${equipment.id};delete from rental_asset where equipment_id=${equipment.id};delete from rental_equipment where id=${equipment.id};`)}
  if(category)sql(`delete from rental_category where id=${category.id}`)
  for(const id of fixtures.customers)sql(`delete from rental_customer where id=${Number(id)} and username like '${prefix}%'`)
  sql(`delete from employee where username='${prefix}'`)
  const directory=resolve(process.env.SKY_UPLOAD_PATH||join(homedir(),'sky-take-out/uploads'))
  for(const url of fixtures.files){assert.match(url,/^\/images\/rental-[a-f0-9-]{36}\.(png|jpg)$/);const target=join(directory,url.slice('/images/'.length));if(existsSync(target))unlinkSync(target)}
  report.cleanup=true
 }catch{report.cleanup=false;process.exitCode=1;console.error('Fixture cleanup failed; inspect isolated verification records')}
 const target=join(root,'sky-server/target/resume-verification');mkdirSync(target,{recursive:true});writeFileSync(join(target,'http-report.json'),JSON.stringify(report,null,2)+'\n');console.log('Report: sky-server/target/resume-verification/http-report.json');console.log('Owned fixture cleanup: '+report.cleanup)
}
