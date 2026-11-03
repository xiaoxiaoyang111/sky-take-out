# 租赁助手与Redis运行说明

## 能力边界

本版是只读租赁助手：固定规则问答、实时库存与报价、本人最近5笔订单、可配置的模型咨询。不是能够代客取消、下单、付款的自主代理；没有向量数据库或Embedding检索。规则来自`RentalAssistantService`中的已确认租赁规则，页面须知见`/guide`。未记录的营业时间、地址、兼容性与赔偿规则不得自行承诺。

电脑端右下角打开咨询侧栏，窄屏使用全屏面板；器材详情的“咨询这款器材”携带型号和当前日期。登录后可查询，管理端不显示助手。三类回复标注“平台租赁规则”“系统实时查询”“模型建议”。没有配置模型时前两类仍可用，自由咨询明确提示未配置。

## 模型配置

最新验收（2026-10-27）：有效密钥配置后，真实DeepSeek首轮回答、连续追问及门店信息/代客操作边界三轮均返回MODEL；首轮1.32秒、追问0.99秒，仅为本次样本。三轮历史经Redis读取验证，测试账号与会话已清理。网页真实点击与窄屏验收仍待补，本机替身测试与早期401记录见下文。

### DeepSeek本机接入（2026-10-27）

在项目根目录打开PowerShell，执行：

```powershell
./scripts/Start-DeepSeekLocal.ps1
```

根据终端提示粘贴API Key，输入被隐藏，不要发到聊天或写进命令参数。脚本只停止本项目记录的演示服务，随后使用`dev,deepseek`配置重启8081后端和8082网页。密钥通过子进程环境传递，不写入仓库、运行状态文件或日志，父进程环境在启动后恢复；下次启动需重新输入。首次缺少构建或代码更新后自行启动时，可传`-Build`。Redis密码仍从本机既有IDEA配置读取。

已配置`DEEPSEEK_API_KEY`环境变量时无需再次输入。若用IDEA运行，设置Active profiles为`dev,deepseek`，在本机Environment variables填写`DEEPSEEK_API_KEY`，再重启；不要同时占用脚本使用的后端端口。

预设接口为`https://api.deepseek.com/chat/completions`，模型`deepseek-flash`（可用`DEEPSEEK_MODEL`覆盖），使用`max_tokens=600`及`thinking:{type:disabled}`。这是当前官方文档中的模型名，不能继续沿用已停用的旧名称；实际服务可用性仍以开放平台和真实响应为准。

启动后用户网页登录，打开助手并确认资料发送说明。可先问“我是摄影新手，第一次拍人像，应该先确认哪些器材需求？”，再追问“如果是在室内呢？”。避免只点规则按钮来判定模型接通：规则和订单查询本来就不调用模型，只有成功显示`MODEL`回复才是实际模型链路成功。

脚本会在停服务之前访问DeepSeek官方`/models`做认证预检，不提交聊天内容。自动去除密钥首尾空白和成对引号，拒绝粘贴整条命令或Bearer前缀。已有环境变量但需要更换密钥时，执行`./scripts/Start-DeepSeekLocal.ps1 -PromptForKey`强制隐藏输入；认证失败保留当前运行实例。一次预检成功仍不代表实际生成回答成功。

2026-10-27首次真实模型测试：配置已加载，发送演示器材问题后返回401，尚无成功模型回复，连续对话未执行；临时账号及Redis数据已清理。需要用户在本机换成有效的DeepSeek开放平台API Key后继续，不应标记真实接入完成。

错误提示区分密钥无效（401）、余额不足（402）、服务限流（429）；不会把供应商响应原文或密钥返回页面，不自动重试付费请求。官方参考：[首次调用](https://api-docs.deepseek.com/quick_start/agent_integrations/)、[请求参数](https://api-docs.deepseek.com/api/create-chat-completion/)、[错误码](https://api-docs.deepseek.com/quick_start/error_codes/)。

故障分类另包含：请求参数不被接受（400/404/422）、服务商临时错误（其他非200）、连接异常、读取超时、无有效文本和JSON格式异常。后端只记录状态、耗时或异常类型，不记录密钥、问题、回答、供应商原始响应或堆栈消息；失败不自动重试。更换本地JAR不会自动更新已运行的复制包，需要通过启动脚本重启；Key不保存在脚本文件中，重启需本机重新输入。

当前已完成适配、本机输入和真实模型三轮联调；以后更换密钥或模型时仍需重新验证，不能把“已配置”当作“已接通”。

### 其他兼容服务

在IDEA运行环境，或启动脚本所在的当前shell中设置以下变量，然后重启后端。密钥只填在本机配置中，不写入仓库、前端或聊天消息。

- `RENTAL_ASSISTANT_ENABLED=true`
- `RENTAL_ASSISTANT_ENDPOINT`：供应商提供的完整Chat Completions URL，包含路径，例如`https://provider.example/v1/chat/completions`，请替换为真实服务地址。
- `RENTAL_ASSISTANT_API_KEY`：本机保存的真实密钥。
- `RENTAL_ASSISTANT_MODEL`：该服务支持的实际模型标识。
- `RENTAL_ASSISTANT_TOKEN_FIELD`：默认`max_completion_tokens`；只支持旧参数的兼容服务可设为`max_tokens`。以实际供应商文档为准。

远程服务必须使用HTTPS；HTTP只允许本机回环测试。采用Java 8兼容的HttpURLConnection，默认连接超时3秒、读取超时45秒，禁止重定向，输出预算600tokens，不自动重试计费请求。可用`RENTAL_ASSISTANT_CONNECT_TIMEOUT_MS`（1～5000）和`RENTAL_ASSISTANT_READ_TIMEOUT_MS`（1～45000）调整，非法值启动时拒绝；读取超时指单次阻塞读取等待，不是整段请求的总时长承诺。上游错误和凭据不返回前端。已配置只代表字段齐全，不代表供应商可用或账户有余额。

### 连接阶段超时与本机代理（2026-10-29）

- 用户重启后日志`elapsedMs=3088`表明此次失败接近3秒连接等待，单独延长读取等待无法解决。实际探测本机直连DeepSeek连接失败，终端代理可达；Java客户端未自动读取终端的`HTTPS_PROXY`。
- 提供可选`RENTAL_ASSISTANT_PROXY_URL`，格式如`http://127.0.0.1:7890`，仅对模型客户端的连接生效，不修改JVM全局代理。留空沿用JVM默认网络选择；需要代理的IDEA运行配置必须显式填写该变量，保存后重启。端口按本机实际代理配置，不能把本地代理地址直接带到服务器部署。
- 仅支持有明确端口的HTTP代理，HTTPS模型请求通过HTTP CONNECT隧道连接；不接受含凭据、查询参数或其他路径的代理URL。错误日志仅记录是否配置代理、连接/读取等待与耗时，不记录代理地址、密钥或用户内容。
- 本机Java通过该代理访问官方`/models`，不携带密钥，812ms收到预期401，证明此次Java代理TLS链路可达，不能当作密钥无效或真实聊天成功。21项模型客户端测试通过，含代理路由、不修改全局属性、非法配置脱敏；真实聊天需运行中的后端加载代理配置后复验。

首次自由咨询前由用户确认资料发送说明，后端同时检查`allowModel=true`。模型收到固定规则、最多50个本店已上架且有未退役实物的型号及选中器材的公开资料、本次问题及至多3轮最近模型对话；本人订单与实时查询回复不加入模型上下文。不要在问题中输入密码、证件或其他敏感信息。器材名称由后端再次核验后交给前端高亮跳转，其他内容按普通文本展示，不执行HTML或模型生成的链接/操作。目录不保证所选日期有可租实物，仍需查实时报价。

实际已验证：本机HTTP替身验证协议、输出预算和错误脱敏；未配置密钥下验证提示与规则/业务查询；配置后完成DeepSeek真实三轮回答及历史保存。本次只验证有限场景，未进行全面回答质量评估，也未核算实际账单费用。

## Redis职责

### 会话与访问保护

- 键前缀`rental:assistant:{customerId}:`，使用已认证身份，不接收客户端customerId。
- `history:sessionId`：最近10轮问答，最后一条成功消息后30分钟过期；会话ID只是分组，不是访问权限。
- 自2026-10-29起按用户要求取消分钟和每日次数上限，不再读取或增加`minute` / `day`计数。旧键不影响咨询，会按原TTL自然过期，无需清空Redis。
- `busy`：同一用户最多一条在途咨询，60秒租约；使用随机持有者标记、Lua比较后删除，避免误删其他请求的锁。
- 清除会话只清除历史。Redis故障时助手返回可读错误，避免绕过并发保护；租赁业务可继续使用。取消项目次数上限不改变供应商自身的服务限流、余额和计费。

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

## 2026-11-03补充
当前前端点击发送即授权该条ASK调用，保留资料发送说明，已取消勾选框。默认每实例最多4个模型生成请求（环境变量RENTAL_ASSISTANT_MAX_CONCURRENT_REQUESTS，1～16），忙时明确拒绝且不重试；不是每日/分钟次数额度。独立小影页、个人中心、生产接入与最新验证以VALIDATION和PRODUCTION为准。
