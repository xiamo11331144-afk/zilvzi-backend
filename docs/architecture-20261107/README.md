# 系统总体架构：配置与源码核对说明

核对日期：2026-11-07（Asia/Shanghai）。本图描述本机开发配置对应的单体系统及外部依赖，不是生产部署验收图。

- 基础提交：`a5fab940e7dd65fcc14aef4adbf4dc5842f7dd7c`。
- 实际依据：该提交加当前工作树（含未提交的引领资格修复）；不把提交号误作当前工作树的完整版本。关键源码摘要见 [source-snapshot.json](./source-snapshot.json)。
- 已保留现有用户文件及未提交修改；本次仅新增本目录的图和说明。

## 图件

- [PNG 图片](./system-architecture.png)：2200 × 1520，可直接插入论文。
- [SVG 矢量图](./system-architecture.svg)：可缩放、可在矢量绘图软件中编辑；字体采用 Microsoft YaHei，异机打开需有兼容中文字体。
- [Mermaid 源码](./system-architecture.mmd)：适合修改节点与调用关系。
- [可复现绘图脚本](./generate_architecture.py)：使用 Pillow 同步生成 PNG 和 SVG。

建议图题：**系统总体架构及主要调用关系**。

图中箭头表示调用或访问的发起方向；响应沿原调用链回传，省略返回箭头。后端框是同一个 Spring Boot 进程，Controller、Service、Mapper 是逻辑分层。图重点为业务 API、文件上传与外部服务调用，省略静态页面资源加载、定时任务内部细节及小程序图片组件按对象 URL 加载图片的浏览器式资源请求。

## 当前配置核对

配置入口为 [application.yml](../../backend/src/main/resources/application.yml#L6)，会尝试导入启动目录下和 backend 目录下的本地覆盖配置。已对白名单字段核对本地覆盖文件，仅记录非敏感配置；未复制该文件或读取运行进程的命令行、令牌。当前工具进程未发现相关 DB / Redis / AI / Aliyun / Spring / Server 环境覆盖变量；这不等于证明已有或历史 Java 进程不存在其他启动参数。

| 组件 | 核对结果 | 配置与实现依据 |
| --- | --- | --- |
| 小程序 | `development`，`http://localhost:8081`；生产 URL 仍为占位值 | [env.js](../../miniprogram/config/env.js#L4) |
| 后端 | 端口8081、根上下文；不是多个独立微服务 | [application.yml](../../backend/src/main/resources/application.yml#L1) |
| MySQL | `localhost:3306/zilvzi`；Druid + JDBC | [application.yml](../../backend/src/main/resources/application.yml#L12) 及本地覆盖白名单核对 |
| Redis | `127.0.0.1:6379` | [application.yml](../../backend/src/main/resources/application.yml#L25) 及本地覆盖白名单核对 |
| AI | `https://api.deepseek.com/chat/completions`，`deepseek-chat`；API key 字段已配置 | [AiServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/AiServiceImpl.java#L22) 及本地覆盖核对 |
| OSS | 杭州 endpoint `oss-cn-hangzhou.aliyuncs.com`；本地覆盖值不含协议前缀；桶名、访问前缀与凭据字段已配置，图中隐藏具体桶标识 | [OssServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/OssServiceImpl.java#L22) 及本地覆盖核对 |
| OCR | endpoint 在代码内固定为 `ocr-api.cn-hangzhou.aliyuncs.com`；凭据字段已配置 | [OcrServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/OcrServiceImpl.java#L17) |
| 微信登录 | 后端使用微信 `jscode2session`；应用标识和凭据字段已配置 | [UserServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/UserServiceImpl.java#L138) |

OSS/OCR 图中标为 SDK 调用，不统一写成 HTTPS：OSS 本地 endpoint 没有显式协议，实际传输协议还取决于 SDK 默认或额外客户端配置。地址和凭据的存在不证明授权有效、账户可用、对象可公开读取或网络可达。

本次本机监听快照未返回8081、3306、6379三个端口的监听记录。未启动服务、连接数据库或 Redis，也未发送可能产生费用的 AI、OSS、OCR 请求。此结论仅适用于核对时刻的本机监听状态，不表示不存在远程、虚拟机、容器内或已停止的服务。

## 逐条调用依据

1. 小程序 → Controller：请求封装经 `wx.request` 发出 HTTP 请求；受保护请求附带 Bearer JWT。文件经 `wx.uploadFile` 上传至后端。依据：[request.js](../../miniprogram/utils/request.js#L41)、[课表上传](../../miniprogram/pages/plan/plan.js#L200)。
2. 后端请求入口与鉴权：登录与管理员拦截器分别保护对应 API，存在公开接口例外；静态管理页由同一后端提供。依据：[WebMvcConfig.java](../../backend/src/main/java/com/zilvzi/config/WebMvcConfig.java#L41)。图中的“Controller 接口层”概括请求入口和外围拦截处理，不表示拦截器属于某个 Controller 类。
3. Controller → Service → Mapper 是主要业务路径，但不是强制隔离。当前 AI 对话、消息、通知、统计、后台等 Controller 存在直接访问 Mapper 的实现，因此图中保留 Controller → Mapper 虚线。例：[AiController.java](../../backend/src/main/java/com/zilvzi/controller/AiController.java#L43)、[MessageController.java](../../backend/src/main/java/com/zilvzi/controller/MessageController.java#L23)。
4. Mapper → MySQL：MyBatis 接口/XML执行数据库操作，Druid 为数据源；业务数据、对话历史与计划模板持久化均在 MySQL。所谓“计划缓存”实际通过模板 Mapper 存取，不是 Redis。依据：[PlanCacheServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/PlanCacheServiceImpl.java#L17)、[MyBatis配置](../../backend/src/main/resources/application.yml#L46)。
5. UserService → RedisUtil → StringRedisTemplate → Redis：读取、删除验证码，生成验证码时写入300秒有效期；未发现将 Redis 作为通用业务数据缓存或 JWT 会话库的调用。验证码短信/邮件发送仍是 TODO，不能据此画出已接入的短信/邮件平台。依据：[UserServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/UserServiceImpl.java#L205)、[RedisUtil.java](../../backend/src/main/java/com/zilvzi/utils/RedisUtil.java#L15)。
6. Service → DeepSeek：AiServiceImpl 通过 Java HttpClient 发出 POST；用于对话、建议与计划生成。计划生成由 PlanService 调用 AI 服务，不由小程序直接携带密钥调用。依据：[AiServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/AiServiceImpl.java#L294)、[PlanServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/PlanServiceImpl.java#L181)。
7. Controller → OssService → OSS：普通上传路径为 `/api/file/upload`；后端执行 `putObject`，返回访问前缀拼接的对象 URL。当前链路不是小程序直传 OSS。依据：[FileUploadController.java](../../backend/src/main/java/com/zilvzi/controller/FileUploadController.java#L29)、[OssServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/OssServiceImpl.java#L49)。
8. 课表上传由 Controller 顺序编排：`/api/file/upload-schedule` → OssService 上传 → 获取图片 URL → OcrService 以 URL 调用阿里云 OCR → 返回文字；不是 OssService 调用 OcrService。OCR → OSS 虚线表示云端根据 URL 读取图片的资源依赖，前提是该 URL 可访问，不是两套业务 SDK 互调。依据：[FileUploadController.java](../../backend/src/main/java/com/zilvzi/controller/FileUploadController.java#L59)、[OcrServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/OcrServiceImpl.java#L23)。
9. OCR → AI 没有直接调用：文字返回小程序后，可被组合为计划请求的时间安排描述，再由 PlanService 使用。依据：[plan.js](../../miniprogram/pages/plan/plan.js#L212) 与 [plan.js](../../miniprogram/pages/plan/plan.js#L258)。因此总体图不添加 OCR → AI 箭头。
10. 微信登录方向为小程序提交 code → 后端 UserService → 微信接口换取 openid → Mapper 查询/注册用户；JWT 由本系统生成。依据：[UserController.java](../../backend/src/main/java/com/zilvzi/controller/UserController.java#L121)、[UserServiceImpl.java](../../backend/src/main/java/com/zilvzi/service/impl/UserServiceImpl.java#L138)。

## 论文配套文字

系统采用微信原生小程序与 Spring Boot 单体后端相结合的前后端分离架构。后端以 Controller、Service 和 MyBatis Mapper 组织接口处理、业务逻辑及数据访问，使用 MySQL 持久化业务数据，Redis 保存短时验证码。部分现有 Controller 直接调用 Mapper。智能对话与计划生成功能通过后端 AI 适配服务请求 DeepSeek 接口；文件由后端上传阿里云 OSS，课表识别由后端将上传所得图片 URL 提交至阿里云 OCR。上述结构依据当前源码及本地配置核对，外部服务调用是否成功须由独立运行验收证明。
