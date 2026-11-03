// Bounded, read-only local benchmark. Not a production-capacity test.
import {performance} from 'node:perf_hooks'
import {mkdirSync,writeFileSync} from 'node:fs'
import {resolve,dirname,join} from 'node:path'
import {fileURLToPath} from 'node:url'
import {cpus,platform,release} from 'node:os'
import assert from 'node:assert/strict'
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),base=process.env.RENTAL_BASE_URL||'http://127.0.0.1:8082'
assert.ok(['localhost','127.0.0.1','[::1]'].includes(new URL(base).hostname),'Local instance only')
const endpoint='/rental/equipment?page=1&pageSize=12',concurrency=8,maxRequests=10000,durationMs=10000,samples=[],failures={}
async function once(){const start=performance.now();let code='network';try{const r=await fetch(base+endpoint,{signal:AbortSignal.timeout(10000)});code=String(r.status);const value=await r.json();if(!r.ok||value.code!==1)throw new Error();return performance.now()-start}catch{failures[code]=(failures[code]||0)+1;return performance.now()-start}}
for(let i=0;i<20;i++)await once()
assert.equal(Object.values(failures).reduce((a,b)=>a+b,0),0,'Warmup failed')
const started=performance.now();let issued=0
await Promise.all(Array.from({length:concurrency},async()=>{while(performance.now()-started<durationMs&&issued<maxRequests){issued++;samples.push(await once())}}))
const seconds=(performance.now()-started)/1000;samples.sort((a,b)=>a-b);const errors=Object.values(failures).reduce((a,b)=>a+b,0)
const report={date:new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'}),base,endpoint,environment:{platform:platform(),os:release(),cpu:cpus()[0]?.model,logicalCpus:cpus().length,node:process.version},warmup:20,concurrency,maxRequests,maxDurationSeconds:durationMs/1000,requests:samples.length,errors,failures,elapsedSeconds:Number(seconds.toFixed(3)),requestsPerSecond:Number((samples.length/seconds).toFixed(2)),p50Ms:Number(samples[Math.ceil(samples.length*.50)-1].toFixed(2)),p95Ms:Number(samples[Math.ceil(samples.length*.95)-1].toFixed(2)),p99Ms:Number(samples[Math.ceil(samples.length*.99)-1].toFixed(2)),maxMs:Number(samples.at(-1).toFixed(2)),scope:'Single local process, warmed public catalog through Nginx. No production traffic, no cold-cache comparison, no capacity or improvement claim.',passed:errors===0}
const directory=join(root,'sky-server/target/resume-verification');mkdirSync(directory,{recursive:true});writeFileSync(join(directory,'benchmark-report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));if(errors)process.exitCode=1
