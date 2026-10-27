import { reactive } from 'vue'
const key = role => `gearhub_${role.toLowerCase()}`
export const auth = reactive({ user: read('USER'), admin: read('ADMIN') })
function read(role) { try { return JSON.parse(localStorage.getItem(key(role))) } catch { return null } }
export const session = role => auth[role === 'ADMIN' ? 'admin' : 'user']
export function saveSession(data) { localStorage.setItem(key(data.role), JSON.stringify(data)); auth[data.role === 'ADMIN' ? 'admin' : 'user'] = data }
export function logout(role) { localStorage.removeItem(key(role)); auth[role === 'ADMIN' ? 'admin' : 'user'] = null }
export async function api(path, { method = 'GET', body, role = 'USER', query, signal } = {}) {
  const params = new URLSearchParams()
  Object.entries(query || {}).forEach(([k, v]) => { if (v !== '' && v !== null && v !== undefined) params.set(k, v) })
  const token = session(role)?.token
  const multipart = body instanceof FormData
  let response
  try { response = await fetch(`/rental${path}${params.size ? `?${params}` : ''}`, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(!multipart && body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, body: body === undefined ? undefined : multipart ? body : JSON.stringify(body), signal }) }
  catch (e) { if (e.name === 'AbortError') throw e; throw new Error('暂时无法连接服务，请检查网络或稍后再试') }
  if (response.status === 401) { logout(role); const target = role === 'ADMIN' ? '/admin/login' : '/login'; if (location.pathname !== target) location.assign(`${target}?redirect=${encodeURIComponent(location.pathname + location.search)}`); throw new Error('登录已失效，请重新登录') }
  if (response.status === 403) throw new Error('当前账户没有执行此操作的权限')
  let result
  try { result = await response.json() } catch { throw new Error('服务返回异常，请稍后重试') }
  if (!response.ok || result.code !== 1) throw new Error(result.msg || `请求失败（${response.status}）`)
  return result.data
}
export const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const statuses = { PENDING: '待门店确认', CONFIRMED: '待领取', RENTED: '租用中', COMPLETED: '已归还', CANCELLED: '已取消', REJECTED: '已拒绝' }
export function today() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
export function tomorrow() { const d = new Date(); d.setDate(d.getDate()+1); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
