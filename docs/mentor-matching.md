# 自愿引领匹配联调说明

## 已实现的规则

- 普通共勉使用 `mutual`；引领使用 `lead`，由申请接收方担任引领者。旧客户端不传模式时按 `mutual` 处理，旧关系迁移后保留为 `mutual`。
- 引领者需主动开启开关。近30天（含今天与29天前）至少24个有效打卡日，且当前连续打卡至少7天。只统计审核通过、日期不晚于今天的记录；同一天多个目标只计一天。今天尚未打卡时，连续天数可从昨天往前计算。
- 引领者近30天有效打卡率必须严格高于申请人；等级不参与资格判断。
- 每名引领者最多3个待处理或已接受的引领申请。发送申请即占用名额，接受不会重复占位，拒绝、解除或拉黑会释放名额。普通共勉关系不占引领名额。
- 推荐、关键词搜索、详情和提交使用统一规则；发送和接受均会重新检查资格、开关、活动差异和名额。
- 关闭引领开关后停止接收新引领申请，已有搭子关系保留。已收到但尚未接受的引领申请继续占位，可拒绝以释放；重新满足条件并开启后方可接受。
- 引领关系解黑后不会自动恢复，需重新申请和校验，防止名额已释放后超额恢复。
- 修改关系使用 `READ_COMMITTED` 事务并按用户ID升序锁定双方用户行，覆盖重发、接受、拒绝、解除、拉黑与解黑。锁后重新读取申请状态，禁用相应查询缓存，避免读到锁前结果。

## 数据库与启动

当前授权的本地数据库已执行[第二阶段增量迁移](../backend/sql/2026-11-07_mentor_matching_rules.sql)，并连续执行两次确认可重复运行。该脚本增加缺少的引领开关、关系模式字段和查询索引，保留现有业务记录。其他环境升级时只执行增量脚本。

[数据库基线](../backend/sql/zilvzi.sql)已同步本功能字段和索引。现有数据库升级时请使用增量脚本；基线脚本包含建表重置语句。

在IDEA重新构建并启动后端，然后在微信开发者工具重新编译小程序。

## 人工联调步骤

1. 使用11号账号“大哥”进入“我的 → 个人信息”，查看近30天有效打卡日、当前连续天数、资格和名额，再开启引领匹配。已准备的24天测试记录保持不变。
2. 切换到近30天有效打卡率低于80%的账号，在“搭子 → 匹配 → 引领型”中查找。可用“学习”目标筛选，11号账号保留了学习类测试目标。
3. 从推荐卡片进入详情页，再发出引领申请；在11号账号收到的申请中检查“引领”标识并接受。
4. 双方“我的搭子”和详情页应保留引领关系标识；申请模式不会因进入详情而变成共勉。
5. 以其他账号继续申请，待处理和已接受合计达到3个时应停止推荐。旧页面继续提交时也会收到名额已满提示；拒绝或解除后恢复名额。
6. 关闭11号账号引领开关，已有搭子关系应保留，新引领申请应停止。可重新开启后继续验收。

## 接口字段

| 接口 | 契约 |
| --- | --- |
| `GET /api/user/mentor/status` | `enabled`、`eligible`、`approvedDays`、`checkinRate`、`streak`、`requiredDays=7`、`requiredApprovedDays=24`、`requiredRate=80`、`capacity=3`、`occupiedSlots`、`pendingCount`、`acceptedCount`、`remainingSlots` |
| `PUT /api/user/mentor` | JSON布尔值 `enabled`，返回最新状态；不接受字符串或数字冒充布尔值 |
| `GET /api/partner/match` | 参数 `goalType`、`keyword`、`matchMode`；候选返回 `mentorEnabled`、`mentorEligible`、`approvedDays`、`checkinRate`、`streak`、`remainingSlots` |
| `POST /api/partner/apply` | `partnerId`、可选 `goalId`、可选 `matchMode`；目标必须属于当前申请人且进行中 |
| `GET /api/partner/list`、`GET /api/partner/requests` | 每条关系或申请返回持久化的 `matchMode` |
| `GET /api/user/detail/{id}` | `mentorStatus`、`leadAvailable`、`leadUnavailableReason`、`relationMatchMode`，打卡率采用近30天口径 |

业务失败仍遵循现有 `{code,message,data}` 协议，小程序以 `code` 判断成功，不能只看HTTP状态码。

## 自动验证

- [资格与锁顺序测试](../backend/src/test/java/com/zilvzi/service/MentorMatchingServiceTest.java)：23项。
- [申请和关系流程测试](../backend/src/test/java/com/zilvzi/service/impl/PartnerMentorWorkflowTest.java)：31项。
- [引领开关测试](../backend/src/test/java/com/zilvzi/service/impl/UserMentorSettingsTest.java)：4项。
- [真实MySQL集成测试](../backend/src/test/java/com/zilvzi/service/MentorMySqlIntegrationTest.java)：5项。仅显式设置 `MENTOR_DB_TESTS=true` 后运行，只连接本机数据源；创建独立测试账号并在每项结束后清理，通知和勋章服务使用模拟实现。

集成测试覆盖有效日期去重、未来/未通过记录排除、昨天连续打卡、30天边界、5个并发申请仅3个成功、占满后接受、拒绝后补位、接受时重新核验、旧模式兼容、拉黑释放与防绕过、同事务重新读取不使用旧缓存。

在配置好Java17与Maven的终端中，从后端目录执行：

```powershell
$env:MENTOR_DB_TESTS='true'
mvn -Dfile.encoding=UTF-8 test
```

不设置此变量时，真实数据库集成测试默认跳过，其他58项测试照常执行。需先应用增量迁移，再运行数据库集成测试。

后端服务和真实MySQL测试不等同于微信开发者工具中的页面视觉验收；小程序最终交互需按上述步骤人工确认。
