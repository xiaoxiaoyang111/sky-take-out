# 光屿器材租赁：网页版 V1 接口契约

状态：开发契约，验收记录见 DEVELOPMENT.md。已有外卖接口保留。所有路径直接访问后端时以 `/rental` 开头，前端同源代理，不经过旧 `/api/` 改写。

## 通用约定

- JSON 返回沿用 `{code:1,msg:null,data:...}`；业务错误 code=0，鉴权失败 HTTP 401，角色不符 HTTP 403。
- 私有接口请求头 `Authorization: Bearer <token>`。用户与员工 token 必须分离验证角色。
- ID 为整数；金额为人民币元，小数最多两位；日期 `yyyy-MM-dd`，时间沿用现有序列化格式 `yyyy-MM-dd HH:mm`。
- 租期为 `[startDate,endDate)`：领取日期包含、归还日期不包含。1日至3日租2天。开始日不得早于今天，最长30天，开始日在未来180天内。
- 一单一件实物。报价和订单金额由后端计算；用户不能指定价格、资产ID或订单状态。
- 列表分页返回 `PageResult {total,records}`，参数 page 默认1，pageSize 默认12，最大100。
- 线下付款及押金交接由门店办理；本版不处理线上支付，不伪造支付成功。

## 身份

- `POST /rental/auth/register`：`{username,password,name}`；username 3～32位字母数字下划线，password 8～72个UTF-8字节，name 1～32字符；注册后返回登录结果。
- `POST /rental/auth/login`：`{username,password}`。
- `POST /rental/auth/admin-login`：`{username,password}`，复用现有员工账户。
- 登录结果 `{token,role:"USER"|"ADMIN",user:{id,username,name}}`。
- `GET /rental/me`：当前身份 `{id,username,name,role}`。

用户密码使用 BCrypt 保存；不在日志中记录密码、token；普通用户不得自行注册管理员。

## 用户公开查询

- `GET /rental/categories` → `[{id,name,sort,status}]`，仅启用分类。
- `GET /rental/equipment?page=1&pageSize=12&categoryId=&keyword=` → PageResult。
- `GET /rental/equipment/{id}` → 型号详情。
- 型号 `{id,categoryId,categoryName,name,brand,description,image,dailyRate,deposit,status,assetCount}`；status 为0停用/1启用。
- `GET /rental/equipment/{id}/availability?startDate=&endDate=` → `{equipmentId,startDate,endDate,availableCount}`。
- `GET /rental/equipment/{id}/quote?startDate=&endDate=` → `{equipmentId,startDate,endDate,rentalDays,dailyRate,rentalAmount,depositAmount,totalAmount,availableCount}`。
- quote 为即时参考，不承诺锁定库存；创建订单必须在事务内再次校验。

## 用户订单（USER）

- `POST /rental/orders`：`{equipmentId,startDate,endDate,requestId,remark}` → 订单详情。requestId 由客户端生成，每个用户唯一、1～64字符；相同参数重试返回同一订单，不同参数复用键拒绝。
- `GET /rental/orders?page=1&pageSize=12&status=` → 本人订单 PageResult。
- `GET /rental/orders/{id}` → 本人订单详情，包含 `events`。
- `POST /rental/orders/{id}/cancel`：`{note}` → 详情，仅 PENDING/CONFIRMED 可取消并释放占用。
- 订单 `{id,orderNo,customerId,equipmentId,assetId,equipmentName,assetCode,startDate,endDate,rentalDays,dailyRate,rentalAmount,depositAmount,status,requestId,remark,returnNote,createdAt,updatedAt,confirmedAt,pickedUpAt,returnedAt,events}`。
- 事件 `{id,orderId,actorRole,actorId,action,note,createdAt}`。

状态机：PENDING 待确认 → CONFIRMED 待领取 → RENTED 租用中 → COMPLETED 已归还。PENDING 可 REJECTED；PENDING/CONFIRMED 可 CANCELLED。前三个状态占用库存。创建时即分配实物；本版不自动过期，待确认单由用户取消或员工拒绝释放。

实物到归还日仍处于 RENTED 时，暂停该实物后续租期的新预约，直到门店登记归还；已预约的下一位顾客也必须等待上一单实物归还后才能领取。正常提前预订仍按租期重叠判断占用。

## 管理端（ADMIN）

- `GET /rental/admin/categories` → 全部分类数组。
- `POST /rental/admin/categories`：`{name,sort,status}` → 分类。
- `PUT /rental/admin/categories/{id}`：同上 → 分类。
- `GET /rental/admin/equipment?page=&pageSize=&categoryId=&keyword=` → 型号 PageResult，含停用型号。
- `POST /rental/admin/equipment`：`{categoryId,name,brand,description,image,dailyRate,deposit,status}` → 型号。
- `PUT /rental/admin/equipment/{id}`：同上 → 型号。
- `GET /rental/admin/equipment/{id}` → 型号详情，包含停用型号，供编辑使用。
- `GET /rental/admin/assets?equipmentId=` → 实物数组 `{id,equipmentId,assetCode,status,createdAt}`。
- `POST /rental/admin/assets`：`{equipmentId,assetCode}` → 实物，默认 READY。
- `PUT /rental/admin/assets/{id}/status`：`{status:"READY"|"MAINTENANCE"|"RETIRED"}` → 实物。有未完成占用订单时不可停用或送修。
- `POST /rental/admin/upload`：multipart字段 file → 图片URL；只支持受限大小、验证实际图片类型的本地图像。
- `GET /rental/admin/orders?page=&pageSize=&status=&keyword=` → 全部订单 PageResult，keyword 搜索订单号/器材名。
- `GET /rental/admin/orders/{id}` → 订单详情。
- `POST /rental/admin/orders/{id}/confirm`：`{note}` → 详情，PENDING → CONFIRMED。
- `POST /rental/admin/orders/{id}/reject`：`{note}` → 详情，PENDING → REJECTED；拒绝原因必填。
- `POST /rental/admin/orders/{id}/pickup`：`{note}` → 详情，CONFIRMED → RENTED，仅租期开始日及以后、归还日前可领取。
- `POST /rental/admin/orders/{id}/return`：`{note}` → 详情，RENTED → COMPLETED，验收说明必填；本版不自动生成赔偿或退款。
- `GET /rental/admin/dashboard` → `{equipmentCount,assetCount,pendingCount,rentedCount,completedCount,rentalRevenue}`，revenue 为已完成订单租金合计，不包括押金、不表示真实在线支付收入。

状态操作重复提交应返回清晰错误或同一结果，不能重复产生资金、库存影响。器材型号维护记录员工和更新时间，订单流转记录操作者与事件时间；历史订单保留名称及金额快照。金额/价格0以上，日租金>0，长度和状态按服务层校验。

## 租赁助手与公开缓存（2026-10-27新增）

- `GET /rental/assistant/status`：公开配置状态`{modelConfigured,historyMinutes,minuteLimit}`，不返回服务地址、密钥或模型内部配置。
- `GET /rental/assistant/history?sessionId=`：USER本人会话，返回最近10轮；sessionId为16～64位字母数字或连字符。
- `POST /rental/assistant/messages`：USER，`{sessionId,message,action,allowModel,equipmentId,startDate,endDate}`。message为1～600字符；action为`ASK`、`RULES`、`QUOTE`、`ORDERS`。QUOTE需型号和有效租期；ORDERS只查本人最近5单。模型咨询需显式`allowModel=true`，规则和业务工具不调用模型。
- 回复`{question,answer,mode,createdAt,sources}`；mode为`RULES`、`LIVE`或`MODEL`，sources为站内来源路径；createdAt为ISO8601时间。模型无配置或不可用时不返回伪造答案。
- `DELETE /rental/assistant/history?sessionId=`：USER清除本人该会话，额度不重置。
- 助手同一用户最多一个在途请求，每60秒6次、每24小时100次，Redis不可用时拒绝咨询并提示；租赁查询和预约不依赖助手可用性。
- 用户端目录缓存60秒，提交分类/型号/实物维护后切换缓存版本；实时库存、报价、订单和预约不缓存。配置与已验证边界见`ASSISTANT.md`。

## 数据表结构

均为 `rental_` 前缀，MySQL InnoDB，utf8mb4：

- customer：id,username,password_hash,display_name,created_at。
- category：id,name,sort,status。
- equipment：id,category_id,name,brand,description,image,daily_rate,deposit,status,created_at,updated_at,create_user,update_user。
- asset：id,equipment_id,asset_code,status,created_at。
- order：id,order_no,customer_id,equipment_id,asset_id,equipment_name,asset_code,start_date,end_date,rental_days,daily_rate,rental_amount,deposit_amount,status,request_id,remark,return_note,created_at,updated_at,confirmed_at,picked_up_at,returned_at。
- order_event：id,order_id,actor_role,actor_id,action,note,created_at。

并发协议：预约与器材/实物状态变更统一先锁 equipment 行；订单状态变更也先锁所属 equipment，再锁 order。占用查询使用当前读，避免事务快照造成超订。用户+request_id、资产编号、订单号均有唯一约束。禁止以 Redis 缓存为唯一库存依据。
