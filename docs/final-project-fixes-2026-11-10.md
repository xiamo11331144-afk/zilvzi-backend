# 最终扫描修复记录（2026-11-10）

本记录对应[原始扫描报告](<final-project-scan-2026-11-10.md>)的12项发现。原报告保留扫描时的现象与测试结果，当前修复及验证结果以本记录为准。未提交或推送代码，未向业务数据库写入修复数据。

## 修复对照

| 原编号 | 修复结果 | 针对性验证 |
| --- | --- | --- |
| 1 后台XSS | 用户及社区列表用DOM文本节点生成，操作参数存入dataset，事件通过监听器处理 | Node隔离DOM测试；未进行真实浏览器攻击测试 |
| 2 管理员撤权 | 后台请求在验证JWT后查询当前账户与角色；账户删除或降级时拒绝旧令牌 | AdminInterceptorTest覆盖有效、降级、删除等路径 |
| 3 拉黑后仍可联系 | 私信检查双向blocked；提醒要求有效accepted搭子关系并排除双向拉黑、自提醒 | MessageControllerTest、PartnerMentorWorkflowTest |
| 4 重复拉黑破坏关系 | 已拉黑记录不再覆盖prev_status，解除时保留原有恢复规则 | 重复拉黑及解除恢复回归 |
| 5 阶段生成并发 | READ_COMMITTED事务；生成计划先锁用户，生成阶段先锁计划并重查已有任务；数据库写入失败不进入AI重试 | PlanGenerationTest验证锁顺序、事务、SQL及失败传播；未执行真实MySQL并发压测 |
| 6 话题编辑绕过校验 | 编辑白名单字段，标题/正文执行敏感词与非空校验，标题长度及置顶值校验 | TopicServiceImplTest |
| 7 排行榜审核口径 | 总打卡和连续日期查询均只统计approved记录 | UserMapperXmlTest验证绑定SQL条件；未实测真实排名数据 |
| 8 登录业务失败 | 验证码、账号登录/注册、微信登录检查业务码；失败提示并阻止错误冷却或保存登录态；重复请求保护 | 登录页隔离测试，模拟HTTP200内业务失败 |
| 9 敏感日志与演示投递 | 删除微信响应、openid和验证码日志；SecureRandom生成六位验证码；HTTPS投递确认后才写Redis，缺配置明确失败 | VerificationCodeTest、VerificationCodeSenderTest；未调用真实短信/邮件商 |
| 10 CSV错列 | UTF-8 BOM、CRLF、字段引号和引号双写，公式前缀作为文本处理 | ExportServiceTest覆盖逗号、引号、多行与公式前缀 |
| 11 PDF分页/字体 | 打包Apache 2.0中文字体，按字体宽度换行、分页并重复页标题；不支持字符显示Unicode编码；生成完成后写响应，失败不返回CSV | ExportServiceTest读取生成PDF验证页数和内容；未做逐页图片视觉验收 |
| 12 后台本机地址 | API改为同源相对地址 | 静态检查与后台Node测试；未做远端部署浏览器验收 |

## 额外交付调整

- 后台统计中尚无可靠口径的日活、打卡率不应以固定0作为真实数据；接口使用未提供状态，统计定义和采集仍需后续业务设计。
- [启动说明](<../README.md>)补充历史审核数据修复脚本的备份、暂停写入、先预览后执行要求。脚本不自动执行，不撤销历史成就。
- 验证码桥接使用环境变量`VERIFICATION_DELIVERY_URL`和`VERIFICATION_DELIVERY_TOKEN`。HTTPS POST包含`target`、`type`、`code`、`expiresInSeconds:300`，须返回2xx且JSON布尔`accepted:true`后才缓存验证码。实际服务商账户与目标环境需另行配置。
- 计划生成采用数据库行锁保持现有结构，锁会跨越AI请求；这保证同一用户/计划串行化，但慢AI会延长事务。后续如需较高吞吐，可改为独立生成状态和后台任务；本次未引入新架构。

## 验证与环境边界

- Java 17离线隔离构建：166项，158通过，8项真实MySQL集成测试按配置跳过，0失败/错误。
- 静态扫描：105个Java源码、24个JS、22个JSON、17个页面、20个Mapper XML、8个SQL文件；语法、页面注册及Mapper方法对应检查无错误，后台无硬编码localhost API基址。
- Node测试：46项全部通过，0失败/跳过。覆盖打卡、引领状态、会员模拟、通知、登录业务失败与重复提交，以及用户/圈子/帖子/评论/话题/回复的后台DOM文本渲染。
- 自动测试主要使用Mock/隔离环境，不代表微信开发者工具编译、真实HTTP、真实浏览器、真实数据库并发或第三方服务已验收。本次没有启动生产服务、执行历史修复SQL或接入真实支付。

## 回归入口

仓库根目录执行：

```powershell
node --test scripts/checkin-regression.test.cjs scripts/vip-miniprogram.test.cjs scripts/admin-xss-regression.test.cjs scripts/login-business-errors.test.cjs .agents/tests/notification-badges.test.cjs
```

后端在Java 17环境执行`mvn -f backend/pom.xml test`。本次使用已有的隔离Maven配置离线运行；真实数据库测试保持`MENTOR_DB_TESTS=false`。
