# 打卡提交与审核流程核对

核对日期：2026-11-07，Asia/Shanghai。依据当前工作树；本次只新增流程图与说明，不修改业务代码、不提交推送、不操作实际数据库。

使用了 [project-analysis 技能](../../.agents/skills/project-analysis/SKILL.md) 追踪小程序→Controller→Service→Mapper→状态与统计回程。

## 图件

- [PNG流程图](./checkin-business-flow.png)：2280×2740，直接插入论文。
- [SVG矢量图](./checkin-business-flow.svg)：缩放或图形编辑。
- [Mermaid源码](./checkin-business-flow.mmd)：编辑业务节点与关系。
- [绘图脚本](./generate_flow.py)：使用Pillow重新生成PNG/SVG；需要Microsoft YaHei中文字体。

建议图题：**打卡提交与搭子审核业务流程图**。实线表示同次请求内的执行；虚线表示提交请求结束后，等待另一次人工审核请求。图中为正常执行主路径及显式业务拒绝分支，未逐节点绘制数据库异常等技术错误。Service事务中向外抛出的运行异常将触发回滚；计划统计刷新和私信更新有局部catch，不能写成所有附加动作必然成功。

## 四类业务路径与三种记录状态

| 业务分支 | checkin.status | 目标连续/累计统计 | 本条路径是否检查成就/加经验 | 任务入口的completed |
| --- | --- | --- | --- | --- |
| 普通打卡：后端未匹配到已接受且同类别搭子 | approved | 立即重算；统计按日期去重 | 调用成就检查；用户存在时加10经验 | 提交时已置1 |
| 待审核：匹配到搭子 | pending | 本条记录不计入approved天数；此时不重算目标缓存统计 | 否 | 提交时已置1 |
| 搭子审核通过 | pending→approved | 此时重算；同日多条不等于多天 | 调用成就检查；用户存在时加10经验 | 不再修改，通常仍为1 |
| 搭子审核驳回 | pending→rejected | 不计入approved天数；驳回分支不调用目标统计更新 | 否 | 不回滚，通常仍为1 |

普通打卡是业务路径名称，不是名为normal的数据库状态。成就检查不等于每次都解锁勋章；经验奖励与按日期去重的累计天数是不同逻辑。

## 主要源码证据

| 规则 | 依据 |
| --- | --- |
| 提交入口，支持taskId/goalId | [CheckinController.java:30](../../backend/src/main/java/com/zilvzi/controller/CheckinController.java#L30) |
| taskId优先；任务存在、所属用户、completed、日期检查 | [CheckinServiceImpl.java:84](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L84) |
| 先更新任务完成标志及计划天数，再创建或返回checkin | [CheckinServiceImpl.java:115](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L115) |
| 断卡任务completed=-1允许补打，记录使用原任务日期 | [CheckinServiceImpl.java:127](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L127) |
| 任务类型优先；目标类别标准化后匹配已接受搭子 | [CheckinServiceImpl.java:134](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L134)、[类型标准化:60](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L60) |
| 待审：插入pending并通知、发送凭证私信 | [CheckinServiceImpl.java:169](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L169) |
| 普通：直接插入approved并更新统计、检查成就 | [CheckinServiceImpl.java:202](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L202) |
| taskId路径已有同任务同日期记录时直接返回 | [CheckinServiceImpl.java:132](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L132)、[返回:210](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L210) |
| goalId兼容路径检查当日重复，使用同一pending/approved分流 | [CheckinServiceImpl.java:214](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L214) |
| 审核接口；只有JSON布尔true被识别为通过 | [CheckinController.java:153](../../backend/src/main/java/com/zilvzi/controller/CheckinController.java#L153) |
| 审核检查、写状态、更新私信、分支通知及统计 | [CheckinServiceImpl.java:360](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L360) |
| 连续/累计统计只计approved，累计按日期去重 | [CheckinMapper.xml:61](../../backend/src/main/resources/mapper/CheckinMapper.xml#L61) |
| 审核写审核人、评语和时间；SQL仅按id更新 | [CheckinMapper.xml:101](../../backend/src/main/resources/mapper/CheckinMapper.xml#L101) |
| 计划完成天数从completed=1的非补发任务统计 | [PlanTaskMapper.xml:106](../../backend/src/main/resources/mapper/PlanTaskMapper.xml#L106) |
| approved路径检查勋章并加10经验 | [AchievementServiceImpl.java:139](../../backend/src/main/java/com/zilvzi/service/impl/AchievementServiceImpl.java#L139) |
| 首页按审核状态显示普通成功/待审/驳回 | [index.js:124](../../miniprogram/pages/index/index.js#L124) |
| 首页有搭子时弹凭证窗口，提交前要求图片 | [index.js:322](../../miniprogram/pages/index/index.js#L322)、[凭证提交:414](../../miniprogram/pages/index/index.js#L414) |
| 打卡页单次/批量提交以及待审、通过和驳回入口 | [checkin.js:222](../../miniprogram/pages/checkin/checkin.js#L222)、[审核:270](../../miniprogram/pages/checkin/checkin.js#L270) |

## 不能补画成已实现能力的部分

1. **驳回后无完整重提闭环。** reviewCheckin没有恢复任务completed，也没有删除旧checkin。再提交同taskId通常先被completed=1拒绝；即使外部把任务恢复为未完成，后续也可能直接返回旧记录。goalId同日已有记录同样被拒绝。不能画“驳回→修改凭证→再次提交成功”。
2. **任务完成与审核通过不同。** pending/rejected记录不计入approved天数，但任务完成标志和计划完成天数可能已变化。日历日期查询没有status过滤，待审/驳回也可能显示为有打卡的日期。依据：[日期SQL](../../backend/src/main/resources/mapper/CheckinMapper.xml#L18)。不能把全站统计统一写成“审核后才计数”。
3. **凭证必填只在部分前端入口。** 首页submitProof要求照片；后端doCheckin未验证凭证非空。打卡页单次和批量入口只发taskId，仍可能创建无凭证的pending。图以“可携带凭证”描述真实接口，不宣称后端强制凭证。
4. **审核权限实现不完整。** reviewCheckin只判断审核人的某条已接受关系的对方是否有同类别目标，没有明确要求这个对方就是该checkin的提交者，也未显式禁止自审。图中只称“现有审核校验”，不能称为完整的提交者搭子身份验证。该问题由代码检查确认，本次未通过接口利用验证。
5. **不能保证先到先得。** 虽然注释写有先到先得，实际先select检查pending，再UPDATE WHERE id；没有WHERE status='pending'、行锁或原子抢占。并发请求可能均通过前置检查并产生重复统计、经验或通知。图中为单请求逻辑，不表示并发安全。
6. **待审列表与提交/审核判定不完全一致。** 列表SQL使用goal_id和原始type条件，部分路径要求active目标；提交/审核使用类别标准化，且没有相同的active过滤。列表中是否可见须单独验收。依据：[待审SQL](../../backend/src/main/resources/mapper/CheckinMapper.xml#L115)。
7. **goalId兼容入口校验较弱。** 与taskId路径不同，当前代码未显式验证目标归属/存在，只检查当日重复后继续分流。因此图中没有把“目标归属已验证”写进校验节点。

以上是当前实现边界，仅写入图和说明；没有修复这些业务逻辑。

## 验证范围

完成相关源码与SQL的逐条追踪、图件目视检查、PNG可读性与SVG XML结构检查。未运行真实接口、数据库写入、微信开发者工具或并发测试；没有把静态分析当作功能验收。未变更业务代码，因此未重复运行整个后端测试集。

## 论文配套描述

打卡提交后，系统根据目标类别与已接受的搭子关系决定直接通过或进入待审核。没有匹配搭子的普通打卡直接保存为approved；存在匹配搭子的打卡保存为pending并发送审核通知。搭子审核通过后，记录更新为approved并触发目标打卡统计与成就检查；审核驳回则更新为rejected并通知提交者。普通打卡和人工审核通过共用approved状态。当前任务完成标志在提交阶段更新，与审核状态分开维护，驳回后尚未形成自动恢复与重新提交的完整闭环。
