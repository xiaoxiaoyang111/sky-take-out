# 本地运行与部署

## 环境

JDK 17（当前本机）、Maven、MySQL 8、Redis、Node.js/npm。沿用 Spring Boot 2.7.3，不迁移到微服务。本机 IDEA 内置 Maven 可执行文件也可使用。

## 数据库初始化

后端仍读取本机既有 `application-dev.yml` 的数据库配置。不要复制其中的真实密码到 README、命令提交或新文件。新部署请通过本机环境或外部配置管理凭据，并替换开发密钥。

先在当前 shell 安全设置 `MYSQL_PWD`，然后执行（替换实际库名和用户名）：

```powershell
./scripts/Initialize-RentalDatabase.ps1 -Database sky_take_out -DatabaseUser root -DemoCatalog
```

只需要空表时不传 `-DemoCatalog`。该脚本只新增 `rental_` 表，不删除或清空旧表。演示目录适用于首次初始化；固定演示ID已占用时不会覆盖。不要把演示目录导入已有正式租赁数据的数据库。

演示数据不创建用户和密码：用户在网页注册，管理端使用已有的已启用员工账号。需要新员工时通过原管理端创建；不要把默认账号当作正式部署的访问凭据。

## 后端

IDEA 运行 `com.sky.SkyApplication`。运行配置环境变量：

- `SKY_REDIS_PASSWORD`：本机 Redis 实际密码；无密码的独立开发Redis可留空。
- `RENTAL_JWT_SECRET`：部署时设置足够长且随机的签名密钥；开发fallback只允许本机试用。
- 数据库使用现有 Spring 数据源配置；正式环境另建外部配置，禁止公开开发配置或凭据。

默认端口8080；本机另有服务占用时在 IDEA 程序参数填 `--server.port=8081`，并让前端代理指向该端口。

```powershell
mvn -pl sky-server -am package
```

不要把“打包成功”当作接口已验证。真实数据库测试需显式设置 `SKY_DB_INTEGRATION=true`；Redis集成测试另设 `SKY_REDIS_INTEGRATION=true`，并提供对应连接配置。测试只清理自己的数据。

## 前端

### 本机一键演示（构建后的网页）

在项目根目录执行：

```powershell
./scripts/Start-RentalLocal.ps1 -Build -UseIdeaRedisConfiguration
```

用户端 `http://localhost:8082/`，管理端 `http://localhost:8082/admin/login`；后端默认8081。脚本使用本机现有Nginx安装，为租赁生成独立配置，不改旧外卖站点。首次先完成数据库初始化。`-Build`只构建，测试仍需单独按上文执行；已有最新构建时可省略。

`-UseIdeaRedisConfiguration`只在当前环境未设置密码时，从本机被Git忽略的SkyApplication运行配置读取Redis密码，不打印、不写入启动文件。也可在当前shell设置`SKY_REDIS_PASSWORD`后不传此开关。脚本为新启动的后端生成临时随机JWT密钥，重启后需重新登录。日志与运行状态在`sky-server/target/rental-local/`。

停止本脚本启动的服务：

```powershell
./scripts/Stop-RentalLocal.ps1
```

若后端端口已有可用租赁服务，启动脚本会复用它，停止脚本不会关闭被复用的服务。本机演示仅监听127.0.0.1；手机真机访问应另按部署配置设置监听地址和网络权限。

### 开发模式

```powershell
cd frontend/rental-web
npm ci
npm run dev
```

默认 `http://localhost:5173`；管理端 `http://localhost:5173/admin/login`。后端8081时，在启动前设置当前shell的 `RENTAL_API_TARGET=http://127.0.0.1:8081`。

手机与电脑在同一网络时，访问 `http://电脑局域网IP:5173`；手机的localhost不会指向电脑。不要为演示关闭系统防火墙，按需要仅放行相应开发端口。

新网页不依赖旧外卖Nginx根目录；旧页面 `http://localhost` 继续保留。Vite代理 `/rental` 和 `/images` 到后端，避免旧Nginx `/api` 路径重写。

## 部署网页

```powershell
cd frontend/rental-web
npm run build
```

将 `dist` 部署到Nginx，采用 `deploy/nginx-rental.conf.example` 的独立站点配置，并替换路径和后端地址。浏览器history路由需要 `try_files ... /index.html`；所有API必须保持 `/rental` 前缀。生产部署使用HTTPS、独立数据库账号、随机JWT密钥和备份；环境配置不提交仓库。

## 最小业务验收

1. 普通用户注册并登录，查看目录与指定租期报价。
2. 用户预约可租设备；重复相同请求不产生第二笔订单。
3. 管理员确认，租期内领取，再登记归还验收。
4. 用户能查看本人订单与事件，不能访问别人的订单。
5. 库存仅剩一台时并发预约不能超订；取消/拒绝正确释放占用。
6. 用户token不能访问员工接口；错误、空数据和不可租状态有明确提示。

## 租赁助手

页面入口、Redis键规则、模型环境变量、额度和验收边界见[助手说明](ASSISTANT.md)。默认模型关闭，规则与实时业务查询可在用户登录且Redis可用时运行。真实密钥配置后须重启后端，再验收真实模型，不能把本机替身测试当作供应商联调。

## Git 提交约定

用户决定何时提交和推送。推荐分为“契约与新增表”“用户鉴权与器材管理”“预约事务与履约”“网页与联调”等真实变更；不要改写已推送历史以伪装项目来源。
