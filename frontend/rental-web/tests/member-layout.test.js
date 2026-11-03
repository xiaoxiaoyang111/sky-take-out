import {before,after,test} from 'node:test'
import assert from 'node:assert/strict'
import {createServer} from 'vite'
import {createSSRApp} from 'vue'
import {renderToString} from '@vue/server-renderer'
import {createRouter,createMemoryHistory} from 'vue-router'
let server,App,Catalog,MyAccount,Profile,Xiaoying,Favorites,Reviews,Orders,auth,createRentalRouter
before(async()=>{
  server=await createServer({server:{middlewareMode:true},appType:'custom'})
  auth=(await server.ssrLoadModule('/src/api.js')).auth
  App=(await server.ssrLoadModule('/src/App.vue')).default
  createRentalRouter=(await server.ssrLoadModule('/src/router.js')).createRentalRouter
  Catalog=(await server.ssrLoadModule('/src/pages/Catalog.vue')).default
  MyAccount=(await server.ssrLoadModule('/src/pages/MyAccount.vue')).default
  Profile=(await server.ssrLoadModule('/src/pages/Profile.vue')).default
  Xiaoying=(await server.ssrLoadModule('/src/pages/Xiaoying.vue')).default
  Favorites=(await server.ssrLoadModule('/src/pages/Favorites.vue')).default
  Reviews=(await server.ssrLoadModule('/src/pages/Reviews.vue')).default
  Orders=(await server.ssrLoadModule('/src/pages/Orders.vue')).default
})
after(async()=>{await server?.close()})
async function render(path){const router=createRentalRouter(createMemoryHistory());const app=createSSRApp(App);app.use(router);await router.push(path);await router.isReady();return renderToString(app)}
test('three user tabs have correct destinations and Xiaoying is in the middle',async()=>{const html=await render('/');const nav=html.match(/<nav[^>]*aria-label="用户主导航"[\s\S]*?<\/nav>/)?.[0];assert.ok(nav);assert.ok(nav.indexOf('推荐')<nav.indexOf('小影'));assert.ok(nav.indexOf('小影')<nav.indexOf('我的'));for(const url of ['/','/assistant','/me'])assert.ok(nav.includes('href="'+url+'"'));assert.ok(html.includes('龙院摄影器材'));assert.ok(!html.includes('光屿'));assert.ok(!html.includes('assistant-panel'))})
test('member page has real transaction destinations and guest login entry',async()=>{const html=await render('/me');for(const text of ['我的交易','我的收藏','全部订单','历史评价','登录 / 注册'])assert.ok(html.includes(text));for(const url of ['/favorites','/orders','/reviews'])assert.ok(html.includes('href="'+url+'"'));assert.ok(!html.includes('0 笔'))})
test('member page shows only an avatar linking to the separate profile page',async()=>{
  auth.user={user:{id:123,name:'测试客户',username:'member_layout_fixture'},role:'USER'}
  try{const html=await render('/me');const toolbar=html.match(/<header class="account-toolbar"[\s\S]*?<\/header>/)?.[0];assert.ok(toolbar);assert.ok(toolbar.includes('href="/me/profile"'));assert.ok(toolbar.includes('aria-label="查看个人信息"'));assert.ok(!toolbar.includes('测试客户'));assert.ok(!toolbar.includes('profile-details'));assert.ok(!html.includes('<h1>我的</h1>'));assert.ok(!html.includes('账号：'));assert.ok(html.includes('我的交易'));assert.ok(!html.includes('class="profile-editor panel"'))}
  finally{auth.user=null}
})
test('separate profile page shows account rows and account actions',async()=>{
  auth.user={user:{id:123,name:'测试客户',username:'member_layout_fixture'},role:'USER'}
  try{const html=await render('/me/profile');for(const text of ['个人信息管理','头像','昵称','登录账号','账号类型','测试客户','member_layout_fixture','切换账号','退出登录'])assert.ok(html.includes(text));assert.ok(html.includes('返回我的'));assert.ok(html.includes('href="/me"'));assert.ok(!html.includes('我的交易'));assert.ok(html.includes('aria-controls="nickname-editor"'))}
  finally{auth.user=null}
})
test('uploaded avatar appears on both account pages and profile provides an image chooser',async()=>{
  auth.user={user:{id:123,name:'测试客户',username:'member_layout_fixture',avatarUrl:'/images/rental-fixture.png'},role:'USER'}
  try{const home=await render('/me'),profile=await render('/me/profile');for(const html of [home,profile])assert.ok(html.includes('src="/images/rental-fixture.png"'));assert.ok(profile.includes('上传或更换头像'));assert.ok(profile.includes('type="file"'));assert.ok(profile.includes('accept="image/png,image/jpeg"'));assert.ok(profile.includes('最大2MB'))}
  finally{auth.user=null}
})
test('Xiaoying is rendered as an independent page, without a floating launcher',async()=>{const html=await render('/assistant');assert.ok(html.includes('assistant-page'));assert.ok(html.includes('你好，我是小影'));assert.ok(!html.includes('assistant-launch'));assert.ok(html.includes('role="region"'))})
test('guest sees Xiaoying greeting and invitation to chat immediately',async()=>{const html=await render('/assistant');assert.ok(html.includes('今天想咨询什么问题？'));assert.ok(html.includes('assistant-greeting'));assert.ok(html.includes('登录后咨询'));assert.ok(!html.includes('assistant-quick'))})
test('signed-in customer enters chat with greeting and without rules or orders shortcuts',async()=>{
  auth.user={user:{id:123,name:'测试客户'},role:'USER'}
  try{const html=await render('/assistant');assert.ok(html.includes('今天想咨询什么问题？'));assert.ok(html.includes('id="assistant-message"'));assert.ok(html.includes('assistant-greeting'));assert.ok(!html.includes('assistant-quick'));assert.ok(!html.includes('>租赁规则</button>'));assert.ok(!html.includes('>本人订单</button>'));assert.ok(!html.includes('可先选择上方问题'))}
  finally{auth.user=null}
})
test('customer can compose without a consent checkbox and still sees model disclosure',async()=>{
  auth.user={user:{id:123,name:'测试客户'},role:'USER'}
  try{const html=await render('/assistant');assert.ok(html.includes('id="assistant-message"'));assert.ok(!html.includes('type="checkbox"'));assert.ok(!html.includes('assistant-consent'));assert.ok(html.includes('咨询内容和当前器材资料会发送给模型服务'));assert.ok(html.includes('订单查询不发送给模型'))}
  finally{auth.user=null}
})
