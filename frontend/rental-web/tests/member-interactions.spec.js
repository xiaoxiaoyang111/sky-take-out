import {beforeEach,afterEach,test,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createMemoryHistory} from 'vue-router'
import App from '../src/App.vue'
import {createRentalRouter} from '../src/router'
import {auth,saveSession} from '../src/api'

let wrapper,fetchMock
const user={id:901,username:'ui_fixture',name:'测试客户',avatarUrl:null}
function login(){saveSession({role:'USER',token:'synthetic-ui-token',user:{...user}})}
function respond(value){return Promise.resolve({ok:true,status:200,json:async()=>({code:1,data:value})})}
beforeEach(()=>{
 localStorage.clear();sessionStorage.clear();auth.user=null;auth.admin=null
 fetchMock=vi.fn((url,options={})=>{
  const path=new URL(url,'http://localhost').pathname
  if(path==='/rental/me')return respond({...user,role:'USER'})
  if(path==='/rental/member/summary')return respond({favorites:2,orders:3,reviews:1})
  if(path==='/rental/member/profile')return respond({...user,name:JSON.parse(options.body).name})
  if(path==='/rental/member/avatar')return respond({avatarUrl:'/images/rental-ui-fixture.png'})
  if(path==='/rental/assistant/status')return respond({modelConfigured:true})
  if(path==='/rental/assistant/history')return respond([])
  if(path==='/rental/assistant/messages')return respond({question:JSON.parse(options.body).message,answer:'推荐人像定焦镜头 · 50mm。',mode:'MODEL',sources:[],equipmentLinks:[{id:6,name:'人像定焦镜头 · 50mm'}]})
  throw new Error('Unexpected fixture request '+path)
 })
 vi.stubGlobal('fetch',fetchMock)
})
afterEach(()=>{wrapper?.unmount();wrapper=null;vi.unstubAllGlobals()})
async function open(path){const router=createRentalRouter(createMemoryHistory());await router.push(path);await router.isReady();wrapper=mount(App,{global:{plugins:[router]}});await flushPromises();return router}
test('guest profile deep link redirects to login and remembers destination',async()=>{const router=await open('/me/profile');expect(router.currentRoute.value.path).toBe('/login');expect(router.currentRoute.value.query.redirect).toBe('/me/profile')})
test('avatar opens standalone profile and nickname save updates session',async()=>{
 login();const router=await open('/me');await wrapper.get('[aria-label="查看个人信息"]').trigger('click');await flushPromises();expect(router.currentRoute.value.path).toBe('/me/profile')
 await wrapper.get('[aria-controls="nickname-editor"]').trigger('click');await wrapper.get('#profile-name').setValue(' 新昵称 ');await wrapper.get('#nickname-editor').trigger('submit');await flushPromises()
 expect(wrapper.text()).toContain('昵称已更新');expect(auth.user.user.name).toBe('新昵称');expect(wrapper.find('#nickname-editor').exists()).toBe(false)
})
test('avatar file is submitted as multipart and updates both image and stored account',async()=>{
 login();await open('/me/profile');const input=wrapper.get('input[type="file"]');Object.defineProperty(input.element,'files',{value:[new File(['fixture bytes'],'avatar.png',{type:'image/png'})],configurable:true});await input.trigger('change');await flushPromises()
 const call=fetchMock.mock.calls.find(([url])=>url==='/rental/member/avatar');expect(call[1].body).toBeInstanceOf(FormData);expect(call[1].body.get('file').name).toBe('avatar.png');expect(call[1].body.has('customerId')).toBe(false);expect(auth.user.user.avatarUrl).toBe('/images/rental-ui-fixture.png');expect(wrapper.get('img[alt="头像"]').attributes('src')).toBe('/images/rental-ui-fixture.png')
})
test('oversized avatar is rejected before network upload',async()=>{
 login();await open('/me/profile');const input=wrapper.get('input[type="file"]');Object.defineProperty(input.element,'files',{value:[new File([new Uint8Array(2*1024*1024+1)],'large.png',{type:'image/png'})]});await input.trigger('change');await flushPromises();expect(wrapper.text()).toContain('不超过2MB');expect(fetchMock.mock.calls.some(([url])=>url==='/rental/member/avatar')).toBe(false)
})
test('Xiaoying sends directly once and renders verified recommendation as internal link',async()=>{
 login();await open('/assistant');expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);await wrapper.get('#assistant-message').setValue('请推荐人像器材');await wrapper.get('.assistant-form').trigger('submit');await flushPromises()
 const calls=fetchMock.mock.calls.filter(([url])=>url==='/rental/assistant/messages');expect(calls).toHaveLength(1);expect(JSON.parse(calls[0][1].body).allowModel).toBe(true);expect(wrapper.get('a.equipment-mention').attributes('href')).toBe('/equipment/6');expect(wrapper.get('#assistant-message').element.value).toBe('')
})
test('failed model request leaves question intact and does not retry automatically',async()=>{
 login();await open('/assistant');fetchMock.mockImplementationOnce(()=>Promise.reject(new TypeError('network failure')));await wrapper.get('#assistant-message').setValue('帮我选相机');await wrapper.get('.assistant-form').trigger('submit');await flushPromises();expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法连接服务');expect(wrapper.get('#assistant-message').element.value).toBe('帮我选相机');expect(fetchMock.mock.calls.filter(([url])=>url==='/rental/assistant/messages')).toHaveLength(1)
})
test('logout clears session and profile route remains protected',async()=>{login();const router=await open('/me/profile');const button=wrapper.findAll('button').find(item=>item.text().includes('退出登录'));await button.trigger('click');await flushPromises();expect(auth.user).toBe(null);expect(localStorage.getItem('gearhub_user')).toBe(null);await router.push('/me/profile');expect(router.currentRoute.value.path).toBe('/login')})
