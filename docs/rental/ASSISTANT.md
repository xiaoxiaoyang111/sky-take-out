# 租赁助手与Redis运行说明

## 能力边界

本版是只读租赁助手：固定规则问答、实时库存与报价、本人最近5笔订单、可配置的模型咨询。不是能够代客取消、下单、付款的自主代理；没有向量数据库或Embedding检索。规则来自`RentalAssistantService`中的已确认租赁规则，页面须知见`/guide`。未记录的营业时间、地址、兼容性与赔偿规则不得自行承诺。

电脑端右下角打开咨询侧栏，窄屏使用全屏面板；器材详情的“咨询这款器材”携带型号和当前日期。登录后可查询，管理端不显示助手。三类回复标注“平台租赁规则”“系统实时查询”“模型建议”。没有配置模型时前两类仍可用，自由咨询明确提示未配置。

## 模型配置

在IDEA运行环境，或启动脚本所在的当前shell中设置以下变量，然后重启后端。密钥只填在本机配置中，不写入仓库、前端或聊天消息。

- `RENTAL_ASSISTANT_ENABLED=true`
- `RENTAL_ASSISTANT_ENDPOINT`：供应商提供的完整Chat Completions URL，包含路径，例如`https://provider.example/v1/chat/completions`，请替换为真实服务地址。
- `RENTAL_ASSISTANT_API_KEY`：本机保存的真实密钥。
- `RENTAL_ASSISTANT_MODEL`：该服务支持的实际模型标识。
- `RENTAL_ASSISTANT_TOKEN_FIELD`：默认`max_completion_tokens`；只支持旧参数的兼容服务可设为`max_tokens`。以实际供应商文档为准。

远程服务必须使用HTTPS；HTTP只允许本机回环测试。连接超时3秒、单次请求15秒，禁止重定向，输出预算600tokens，不自动重试计费请求。上游错误和凭据不返回前端。已配置只代表字段齐全，不代表供应商可用或账户有余额。

首次自由咨询前由用户确认资料发送说明，后端同时检查`allowModel=true`。模型仅收到固定规则、当前公开器材资料、本次问题及至多3轮最近模型对话；本人订单与实时查询回复不加入模型上下文。不要在问题中输入密码、证件或其他敏感信息。返回内容按普通文本展示，不执行HTML或模型生成的操作。

实际已验证：本机HTTP替身验证协议、输出预算和错误脱敏；未配置密钥下验证提示与规则/业务查询。尚未验证真实供应商、实际费用及真实模型回答质量。用户提供服务并在本机配置后，再做真实供应商验收。

## Redis职责

### 会话与访问保护

- 键前缀`rental:assistant:{customerId}:`，使用已认证身份，不接收客户端customerId。
- `history:sessionId`：最近10轮问答，最后一条成功消息后30分钟过期；会话ID只是分组，不是访问权限。
- `minute` / `day`：每个用户每60秒最多6次、每24小时最多100次，Lua原子检查并增加，首次使用时设置过期。失败请求和清除操作也消耗额度。
- `busy`：同一用户最多一条在途咨询，60秒租约；使用随机持有者标记、Lua比较后删除，避免误删其他请求的锁。
- 清除会话只清除历史，不清零额度。Redis故障时助手返回可读错误，不绕过限流调用模型；租赁业务可继续使用。

### 公开目录缓存

- 缓存用户端分类、型号分页和型号详情，TTL60秒；管理端读取、报价、库存、订单与预约不走该缓存。
- 分类、型号、实物新增或修改事务提交后切换全局目录版本。回源读取后通过Lua校验版本再写入，避免旧读取污染新版本。
- Redis故障时目录查询回源MySQL。若失效通知在Redis故障时失败，旧条目最多保留原TTL；显示信息允许短时滞后，预约价格和可租数量仍由数据库重新校验。
- 本版不引入Redis分布式库存锁，实物分配仍由MySQL事务和行锁保护。

## 本机验证

```powershell
# 当前shell先配置好实际Redis连接；真实数据库测试会使用项目当前数据源。
$env:SKY_DB_INTEGRATION='true'
$env:SKY_REDIS_INTEGRATION='true'
mvn -pl sky-server -am package
cd frontend/rental-web
npm test
npm run build
```

测试创建并清理自己的数据库/Redis数据，禁止FLUSHDB。模型协议测试使用本机替身，不需要真实密钥。

协议参考：[Chat Completions API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[Spring Data Redis 2.7脚本支持](https://docs.spring.io/spring-data/redis/docs/2.7.2/reference/html/#scripting)。目前采用兼容协议以便更换供应商，未绑定某个模型或SDK。
