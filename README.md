# 自律搭子监督与习惯养成平台

微信原生小程序 + Spring Boot 后端，提供用户登录注册、目标管理、AI 学习计划、每日任务打卡、搭子互助审核、圈子互动、私信通知、成就体系与后台管理。当前仓库同时包含后端和小程序。

## 项目结构

```text
backend/
  pom.xml                 Maven 构建配置
  src/main/java/          Java 后端
  src/main/resources/     公开配置、MyBatis XML、后台管理页面
  src/test/               后端自动测试
  sql/                    数据库结构与增量升级脚本
  application-local.example.yml  本机配置示例
miniprogram/              微信原生小程序，直接用微信开发者工具导入
scripts/                  小程序测试与隔离构建准备工具
docs/                     功能说明与架构图
.agents/                  项目开发约定及消息角标测试
```

IDE 设置、编译输出、缓存、个人 Word 文档及一次性文档工具不属于运行所需内容。整理时已保存在原电脑的本地归档目录，不上传到仓库。

## 环境要求

- JDK 17、Maven 3.8+
- MySQL 8、Redis
- 微信开发者工具

## 数据库初始化与升级

先创建数据库：

```sql
CREATE DATABASE zilvzi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

全新空库先导入 `backend/sql/zilvzi.sql`，再依次运行以下增量脚本。已有业务库先备份，直接按需执行增量脚本，**不要重新导入包含重建表操作的基础脚本**。

1. `backend/sql/2026-06-28_checkin_task_fix.sql`：多计划任务打卡索引修复。
2. `backend/sql/2026-11-07_mentor_matching_rules.sql`：引领开关、匹配模式与相关索引（已包含旧的引领开关迁移）。
3. `backend/sql/2026-11-07_vip_membership.sql`：会员有效期与订单表。

执行前选中 `zilvzi` 数据库。以上三个增量脚本会检查已存在的字段、索引或表，支持重复执行。Redis 启动即可，无需导入 SQL。

`mentor_test_user_11_seed.sql` / `mentor_test_user_11_cleanup.sql` 是针对固定测试用户的开发辅助脚本，可能改变该用户的测试目标和打卡；不属于初始化步骤，不在真实业务库执行。

## 后端配置与启动

仓库中的 `backend/src/main/resources/application.yml` 使用环境变量，不包含本机真实密码或云服务密钥。首次克隆可复制配置示例：

```powershell
Copy-Item backend/application-local.example.yml backend/application-local.yml
```

按本机情况填写复制后的文件；它已被 Git 忽略。启动目录为仓库根目录或 `backend/` 时会自动导入该本机文件。原电脑已保留可用的本机配置，无需重复复制覆盖。

必要配置是数据库连接和 JWT 签名密钥。JWT 密钥应为至少 32 字节的随机字符串；可以在当前 PowerShell 会话中生成并设置：

```powershell
$env:JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '<填写本机数据库密码>'
```

也可通过 IDEA 的运行配置设置环境变量。常用变量如下：

| 类别 | 环境变量 |
| --- | --- |
| MySQL | `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` |
| JWT | `JWT_SECRET`（必填） |
| 首次管理员 | `ADMIN_BOOTSTRAP_USERNAME`、`ADMIN_BOOTSTRAP_PASSWORD` |
| AI | `AI_API_URL`、`AI_API_KEY`、`AI_MODEL` |
| 微信登录 | `WECHAT_APP_ID`、`WECHAT_SECRET` |
| 阿里云 OSS | `ALIYUN_OSS_ENDPOINT`、`ALIYUN_OSS_ACCESS_KEY_ID`、`ALIYUN_OSS_ACCESS_KEY_SECRET`、`ALIYUN_OSS_BUCKET_NAME`、`ALIYUN_OSS_URL_PREFIX` |
| 阿里云 OCR | `ALIYUN_OCR_ACCESS_KEY_ID`、`ALIYUN_OCR_ACCESS_KEY_SECRET` |

第三方服务未配置时，对应 AI、微信登录、OSS 上传或 OCR 功能不可用。微信后端 AppID 应与小程序项目 AppID 一致。已有账户可通过项目提供的账号登录入口测试核心功能。

默认不创建管理员。首次需要创建时，显式设置 `ADMIN_BOOTSTRAP_PASSWORD`；同名管理员已存在时不会重置其密码，同名普通用户也不会被自动提权。完成初始化后可移除该启动配置。

确认 MySQL 和 Redis 已启动后：

```powershell
Set-Location backend
mvn spring-boot:run
```

或用 IDEA（JDK 17）运行 `com.zilvzi.ZilvziApplication`。后端地址为 `http://localhost:8081`，后台管理页面为 `http://localhost:8081/admin`。

## 小程序运行

1. 用微信开发者工具导入 `miniprogram/`，使用你有开发权限的 AppID。
2. `miniprogram/config/env.js` 的 development 地址默认为 `http://localhost:8081`，适合电脑模拟器。
3. 在开发者工具本地设置中开启“不校验合法域名”，编译运行。
4. 真机访问时，将 development 地址改为电脑局域网 IP，并确保手机能访问后端端口；正式发布须配置合法 HTTPS 域名。

## VIP 开发模拟

仅发起新的引领申请需要有效 VIP，共勉匹配免费，到期不影响已有申请与关系。默认禁用模拟开通，尚未接入真实支付。

开发测试时，在 IDEA 的程序参数中添加：

```text
--spring.profiles.active=dev --vip.payment.channel=mock --vip.mock-payment.enabled=true
```

只有活动环境全部属于 `dev` / `test`，且渠道与开关同时满足条件时才允许模拟确认。默认套餐为 30 天、990 分的测试示例，无真实扣款。

详细 API、订单规则和验收说明见 [会员功能说明](docs/vip-membership.md)，引领匹配规则见 [引领匹配说明](docs/mentor-matching.md)。

## 自动检查

小程序测试与静态检查在仓库根目录执行：

```powershell
node --test scripts/vip-miniprogram.test.cjs
node --test .agents/tests/notification-badges.test.cjs
node scripts/prepare-vip-verification.cjs
```

后端使用 Java 17，可在 `backend/` 执行 `mvn test`。真实 MySQL 集成测试默认跳过；不要在未准备独立测试库时启用 `MENTOR_DB_TESTS=true`。隔离构建方式见会员文档。

## 配置与发布注意

- 不提交 `application-local.yml`、`.env`、私钥、真实令牌或数据库备份；本地配置及构建输出已列入 `.gitignore`。
- `.env` 不会由 Spring Boot 自动加载；使用系统/IDE 环境变量或上述本机 YAML 文件。
- 项目当前验证码仍为演示实现，会写入 Redis 并打印到后端控制台；公开部署前需要接入真实短信或邮件服务，并清理敏感日志。
- 本次整理未连接或迁移实际数据库，未调用真实支付，也未启动线上服务。
