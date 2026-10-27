# 光屿 GearHub 网页端

Vue 3 + Vue Router + Vite，有源码的用户端与门店管理端，旧外卖前端不受影响。

## 启动

```powershell
npm ci
# 可选：后端不在 8080 时设置
$env:RENTAL_API_TARGET = 'http://127.0.0.1:8081'
npm run dev
```

默认地址 http://localhost:5173；同一局域网手机可使用电脑 IP 的 5173 端口。需要允许本地防火墙访问。开发代理将 `/rental`、`/images` 转发给后端。不要使用旧 `/api/` 重写。

`npm run build` 输出 `dist/`。部署时配置 history fallback 到 index.html，且 `/rental`、`/images` 优先转发后端。Vite preview 只用于静态预览，不代替生产反向代理。

## 路由

- `/`：公开器材目录，分类、搜索、分页。
- `/equipment/:id`：日期报价、可租数量、提交预约。
- `/login`：用户注册 / 登录；`/orders`：本人订单和流转记录。
- `/admin/login`：现有员工登录；`/admin`：门店概览。
- `/admin/equipment`：分类、器材型号、上传、实物登记与状态。
- `/admin/orders`：预约审核、领取、归还验收。

角色分别保存 token；鉴权仍由后端执行。401 清理对应身份并回到对应登录页。客户端显示后端实际业务错误，不将请求失败包装为成功。

租期 `[startDate, endDate)`，归还日不计费；报价不锁库存，提交时重新校验。创建预约为每次参数生成独立 requestId，网络重试复用，编辑租期/备注后更换。租金与押金线下交接，页面不提供支付模拟。

四张 SVG 插画为项目本地素材，不依赖远程图片。UI 使用系统中文字体。接口契约见仓库 `docs/rental/API.md`。
