# 数据库 E-R 图及结构核对

核对范围：当前基线DDL + README指定的3份正式增量脚本；未连接实际数据库。基线包含21表，189字段，29个物理外键，另列4个明确逻辑关联。

## 图件与读法

- [总图PNG](./database-er.png) / [总图SVG](./database-er.svg)：覆盖21表。采用按子表展开的关系图布局，每个父实体小框只是同名表引用。
- [核心业务PNG](./er-core.png) / [核心业务SVG](./er-core.svg)。
- [社交互动PNG](./er-social.png) / [社交互动SVG](./er-social.svg)。
- [支撑与会员PNG](./er-support.png) / [支撑与会员SVG](./er-support.svg)。
- [完整Mermaid E-R源码](./database-er.mmd)：包含全部字段及完整FK/LR连线，适合全局布局编辑。
- [结构清单JSON](./schema-inventory.json) / [生成脚本](./generate_er.py)。

PK=主键；FK=DDL明确声明的外键；LR=有语义和使用依据但没有外键约束的逻辑引用；UQ=唯一索引（组合列共同唯一）；NN=NOT NULL；NULL=可空；POLY=根据类型解释目标ID的条件/多态引用。

数字基数按“父实体 : 子实体”读取：1 : 0..N表示每条子记录必须引用一条父记录，而一个父记录可以没有或有多条子记录；0..1 : 0..N表示子记录允许没有父记录。会员有效期为1 : 0..1：每个用户最多一条会员记录，用户可以没有会员记录。逻辑关联的基数只表示预期有效业务数据，不承诺数据库引用完整性。

Mermaid中实线/虚线特意编码FK/LR，**不使用其线型表达标识/非标识关系**；端点乌鸦脚表达基数。图中省略部分普通属性，完整字段在下方数据字典和Mermaid中。

## SQL来源与增量合并

| 来源 | 核对结果 |
|---|---|
[基线](../../backend/sql/zilvzi.sql) | 含当前21张表，已包含正式增量目标结构。
[打卡增量](../../backend/sql/2026-06-28_checkin_task_fix.sql) | task_id、uk_task_date及查询索引已存在；旧uk_user_goal_date已不存在，现为普通idx_user_goal_date。
[引领增量](../../backend/sql/2026-11-07_mentor_matching_rules.sql) | user.mentor_enabled、partner.match_mode及两个索引已在基线中。
[VIP增量](../../backend/sql/2026-11-07_vip_membership.sql) | 两表与外键均已存在；CREATE IF NOT EXISTS不会覆盖基线表定义。

[旧引领脚本](../../backend/sql/2026-11-06_mentor_matching.sql)仅为已被新脚本覆盖的旧迁移；不会在当前基线上再次无条件ADD同名字段。固定用户seed/cleanup脚本不作为正式结构迁移。合并为静态核对，未执行任何SQL。

## 完整关系清单

| ID | 子表字段 → 父表键 | 类别 | 父 : 子基数 | 删除行为 |
|---|---|---|---|---|
| F01 | achievement.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F02 | checkin.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F03 | checkin.goal_id → goal.id | FK | 1 : 0..N | CASCADE |
| F04 | circle.creator_id → user.id | FK | 1 : 0..N | CASCADE |
| F05 | circle_member.circle_id → circle.id | FK | 1 : 0..N | CASCADE |
| F06 | circle_member.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F07 | comment.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F08 | comment.post_id → post.id | FK | 1 : 0..N | CASCADE |
| F09 | file_upload.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F10 | goal.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F11 | like_record.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F12 | like_record.post_id → post.id | FK | 1 : 0..N | CASCADE |
| F13 | message.from_user_id → user.id | FK | 1 : 0..N | CASCADE |
| F14 | message.to_user_id → user.id | FK | 1 : 0..N | CASCADE |
| F15 | notification.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F16 | partner.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F17 | partner.partner_id → user.id | FK | 1 : 0..N | CASCADE |
| F18 | partner.goal_id → goal.id | FK | 0..1 : 0..N | SET NULL |
| F19 | plan.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F20 | plan.goal_id → goal.id | FK | 0..1 : 0..N | SET NULL |
| F21 | plan_task.plan_id → plan.id | FK | 1 : 0..N | CASCADE |
| F22 | post.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F23 | post.circle_id → circle.id | FK | 0..1 : 0..N | SET NULL |
| F24 | topic.circle_id → circle.id | FK | 1 : 0..N | CASCADE |
| F25 | topic.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F26 | topic_reply.topic_id → topic.id | FK | 1 : 0..N | CASCADE |
| F27 | topic_reply.user_id → user.id | FK | 1 : 0..N | CASCADE |
| F28 | vip_membership.user_id → user.id | FK | 1 : 0..1 | CASCADE |
| F29 | vip_order.user_id → user.id | FK | 1 : 0..N | CASCADE |
| L01 | ai_chat_history.user_id → user.id | LR | 1 : 0..N | 无数据库外键约束 |
| L02 | checkin.task_id → plan_task.id | LR | 0..1 : 0..N | 无数据库外键约束 |
| L03 | checkin.partner_id → user.id | LR | 0..1 : 0..N | 无数据库外键约束 |
| L04 | notification.from_user_id → user.id | LR | 0..1 : 0..N | 无数据库外键约束 |

除SET NULL的partner.goal_id、plan.goal_id、post.circle_id外，基线其余物理外键ON DELETE均为CASCADE；ON UPDATE均为RESTRICT。VIP增量省略ON UPDATE，但当前基线表显式为RESTRICT。

## 关键基数与约束边界

- user与vip_membership为1对0..1，因为vip_membership.user_id同时是PK与FK；不是1对多。vip_order与用户为多对1，未建立vip_order到vip_membership的外键。
- circle_member将用户和圈子展开为多对多，UNIQUE(circle_id,user_id)限制同一成员组合唯一。like_record将用户和动态展开为多对多，UNIQUE(user_id,post_id)限制同一点赞组合唯一。
- partner的user_id与partner_id都引用user.id，是两个角色的外键；UNIQUE(user_id,partner_id)只约束有序用户对，不能自动禁止反向重复或自身关联。checkin.partner_id实际是审核用户ID，不是partner关系表ID。
- goal到plan是1对0..N，plan.goal_id可空且不唯一；不能画成一目标只允许一个计划。task到checkin逻辑上也是1对0..N，UNIQUE(task_id,date)仅保证每个任务每日期最多一条，不是task_id全局唯一。
- checkin.task_id允许NULL。MySQL唯一索引允许多条含NULL的组合，因此uk_task_date和uk_user_task_date不能保证普通目标打卡每天只有一条。用户+目标+日期当前只有普通索引，应用层判重不等于数据库唯一约束。
- achievement的UNIQUE(user_id,medal_name)约束同用户同勋章名唯一，不表示每用户只有一个成就。user的username/phone/email/openid是分别可空的单列唯一索引，不是四列组合唯一。
- plan_template没有外键，plan没有template_id；goal_type、goal_keyword用于类型/关键词匹配，不能虚构plan_template到plan或goal的固定外键。
- file_upload.related_type/related_id和notification.related_type/related_id是通用类型+ID字段；DDL不能确定唯一目标表。message.related_id根据type解释，例如checkin_proof对应checkin.id。这些均不画成无条件物理外键，也不推定所有可能目标。
- DDL可表达每条记录引用哪个父记录，但不自动保证checkin.user_id与goal.user_id一致，或plan.user_id与goal.user_id一致；图中基数不代表这些跨表业务一致性已受约束。

## 逻辑关联依据

- L01 ai_chat_history.user_id→user.id：基线用户ID字段与索引，没有FK；[AiController](../../backend/src/main/java/com/zilvzi/controller/AiController.java)按登录userId存取历史。
- L02 checkin.task_id→plan_task.id：基线无FK；[今日任务SQL](../../backend/src/main/resources/mapper/CheckinMapper.xml#L25)按task_id连接，[打卡服务](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L84)按taskId读取任务。
- L03 checkin.partner_id→user.id：基线无FK；[审核SQL](../../backend/src/main/resources/mapper/CheckinMapper.xml#L101)写入审核用户ID，[待审目标查询](../../backend/src/main/resources/mapper/CheckinMapper.xml#L107)连接user。
- L04 notification.from_user_id→user.id：基线无FK且字段可空；[通知Mapper](../../backend/src/main/resources/mapper/NotificationMapper.xml)按用户关联发送者信息。

## 全量字段与唯一约束

### achievement（成就）

[基线DDL第24行](../../backend/sql/zilvzi.sql#L24)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F01 FK→user.id |
| medal_name | varchar(50) | 否 | — |
| medal_icon | varchar(20) | 是 | — |
| medal_color | varchar(20) | 是 | — |
| condition | varchar(200) | 是 | — |
| unlock_time | datetime | 是 | — |

唯一索引：uk_user_medal(user_id, medal_name)。

### ai_chat_history（AI对话历史）

[基线DDL第42行](../../backend/sql/zilvzi.sql#L42)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | L01 LR→user.id |
| role | varchar(20) | 否 | — |
| content | text | 否 | — |
| session_type | varchar(20) | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### checkin（打卡记录）

[基线DDL第59行](../../backend/sql/zilvzi.sql#L59)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F02 FK→user.id |
| goal_id | bigint | 否 | F03 FK→goal.id |
| task_id | bigint | 是 | L02 LR→plan_task.id |
| content | varchar(500) | 是 | — |
| image | varchar(255) | 是 | — |
| checkin_time | datetime | 是 | — |
| date | date | 否 | — |
| status | varchar(20) | 是 | — |
| partner_id | bigint | 是 | L03 LR→user.id |
| proof_image | varchar(500) | 是 | — |
| proof_content | text | 是 | — |
| review_time | datetime | 是 | — |
| review_comment | varchar(200) | 是 | — |

唯一索引：uk_user_task_date(user_id, task_id, date)；uk_task_date(task_id, date)。

### circle（圈子）

[基线DDL第91行](../../backend/sql/zilvzi.sql#L91)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| creator_id | bigint | 否 | F04 FK→user.id |
| name | varchar(100) | 否 | — |
| description | varchar(500) | 是 | — |
| cover | varchar(255) | 是 | — |
| tags | varchar(255) | 是 | — |
| member_count | int | 是 | — |
| post_count | int | 是 | — |
| create_time | datetime | 是 | — |
| update_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### circle_member（圈子成员）

[基线DDL第111行](../../backend/sql/zilvzi.sql#L111)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| circle_id | bigint | 否 | F05 FK→circle.id |
| user_id | bigint | 否 | F06 FK→user.id |
| role | int | 是 | — |
| status | int | 是 | — |
| join_time | datetime | 是 | — |

唯一索引：uk_circle_user(circle_id, user_id)。

### comment（评论）

[基线DDL第130行](../../backend/sql/zilvzi.sql#L130)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F07 FK→user.id |
| post_id | bigint | 否 | F08 FK→post.id |
| content | varchar(500) | 否 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### file_upload（上传记录）

[基线DDL第147行](../../backend/sql/zilvzi.sql#L147)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F09 FK→user.id |
| file_name | varchar(255) | 否 | — |
| file_path | varchar(500) | 否 | — |
| file_type | varchar(50) | 是 | — |
| file_size | bigint | 是 | — |
| related_type | varchar(50) | 是 | — |
| related_id | bigint | 是 | — |
| ai_parse_result | text | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### goal（目标）

[基线DDL第167行](../../backend/sql/zilvzi.sql#L167)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F10 FK→user.id |
| name | varchar(100) | 否 | — |
| type | varchar(20) | 否 | — |
| description | varchar(500) | 是 | — |
| start_date | date | 是 | — |
| end_date | date | 是 | — |
| status | varchar(20) | 是 | — |
| streak | int | 是 | — |
| total_checkins | int | 是 | — |
| create_time | datetime | 是 | — |
| update_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### like_record（点赞）

[基线DDL第190行](../../backend/sql/zilvzi.sql#L190)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F11 FK→user.id |
| post_id | bigint | 否 | F12 FK→post.id |
| create_time | datetime | 是 | — |

唯一索引：uk_user_post(user_id, post_id)。

### message（私信）

[基线DDL第207行](../../backend/sql/zilvzi.sql#L207)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| from_user_id | bigint | 否 | F13 FK→user.id |
| to_user_id | bigint | 否 | F14 FK→user.id |
| content | varchar(500) | 否 | — |
| type | varchar(20) | 是 | — |
| related_id | bigint | 是 | — |
| is_read | int | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### notification（通知）

[基线DDL第227行](../../backend/sql/zilvzi.sql#L227)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F15 FK→user.id |
| from_user_id | bigint | 是 | L04 LR→user.id |
| type | varchar(50) | 否 | — |
| title | varchar(100) | 是 | — |
| content | varchar(500) | 是 | — |
| related_type | varchar(50) | 是 | — |
| related_id | bigint | 是 | — |
| is_read | int | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### partner（搭子关系）

[基线DDL第248行](../../backend/sql/zilvzi.sql#L248)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F16 FK→user.id |
| partner_id | bigint | 否 | F17 FK→user.id |
| goal_id | bigint | 是 | F18 FK→goal.id |
| match_mode | varchar(10) | 否 | — |
| status | varchar(20) | 是 | — |
| prev_status | varchar(20) | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：uk_user_partner(user_id, partner_id)。

### plan（计划）

[基线DDL第272行](../../backend/sql/zilvzi.sql#L272)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F19 FK→user.id |
| goal_id | bigint | 是 | F20 FK→goal.id |
| name | varchar(100) | 是 | — |
| total_days | int | 是 | — |
| completed_days | int | 是 | — |
| schedule_data | text | 是 | — |
| remark | text | 是 | — |
| status | varchar(20) | 是 | — |
| create_time | datetime | 是 | — |
| update_time | datetime | 是 | — |
| outline | text | 是 | — |
| current_phase | int | 是 | — |

唯一索引：无（主键除外）。

### plan_task（计划任务）

[基线DDL第297行](../../backend/sql/zilvzi.sql#L297)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| plan_id | bigint | 否 | F21 FK→plan.id |
| day_number | int | 否 | — |
| task_date | date | 是 | — |
| task_name | varchar(100) | 是 | — |
| task_type | varchar(50) | 是 | — |
| task_description | varchar(500) | 是 | — |
| task_duration | varchar(20) | 是 | — |
| task_icon | varchar(20) | 是 | — |
| task_color | varchar(20) | 是 | — |
| sort_order | int | 是 | — |
| completed | int | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### plan_template（方案模板）

[基线DDL第322行](../../backend/sql/zilvzi.sql#L322)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| goal_type | varchar(50) | 否 | — |
| goal_keyword | varchar(100) | 否 | — |
| total_days | int | 否 | — |
| difficulty | varchar(20) | 是 | — |
| plan_outline | text | 是 | — |
| daily_tasks_template | text | 是 | — |
| ai_model | varchar(50) | 是 | — |
| usage_count | int | 是 | — |
| success_count | int | 是 | — |
| quality_score | float | 是 | — |
| create_time | datetime | 是 | — |
| update_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### post（动态）

[基线DDL第346行](../../backend/sql/zilvzi.sql#L346)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| user_id | bigint | 否 | F22 FK→user.id |
| circle_id | bigint | 是 | F23 FK→circle.id |
| content | text | 否 | — |
| images | text | 是 | — |
| like_count | int | 是 | — |
| comment_count | int | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### topic（话题）

[基线DDL第367行](../../backend/sql/zilvzi.sql#L367)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| circle_id | bigint | 否 | F24 FK→circle.id |
| user_id | bigint | 否 | F25 FK→user.id |
| title | varchar(200) | 否 | — |
| content | text | 是 | — |
| is_pinned | int | 是 | — |
| reply_count | int | 是 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### topic_reply（话题回复）

[基线DDL第387行](../../backend/sql/zilvzi.sql#L387)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| topic_id | bigint | 否 | F26 FK→topic.id |
| user_id | bigint | 否 | F27 FK→user.id |
| content | varchar(1000) | 否 | — |
| create_time | datetime | 是 | — |

唯一索引：无（主键除外）。

### user（用户）

[基线DDL第404行](../../backend/sql/zilvzi.sql#L404)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| username | varchar(50) | 是 | — |
| password | varchar(100) | 是 | — |
| phone | varchar(20) | 是 | — |
| email | varchar(100) | 是 | — |
| nickname | varchar(50) | 是 | — |
| avatar | varchar(255) | 是 | — |
| bio | varchar(500) | 是 | — |
| level | int | 是 | — |
| experience | int | 是 | — |
| role | int | 是 | — |
| mentor_enabled | tinyint | 否 | — |
| openid | varchar(100) | 是 | — |
| create_time | datetime | 是 | — |
| update_time | datetime | 是 | — |
| province | varchar(50) | 是 | — |
| city | varchar(50) | 是 | — |
| district | varchar(50) | 是 | — |

唯一索引：username(username)；phone(phone)；email(email)；openid(openid)。

### vip_membership（会员有效期）

[基线DDL第437行](../../backend/sql/zilvzi.sql#L437)；主键：user_id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| user_id | bigint | 否 | PK；F28 FK→user.id |
| vip_until | datetime | 是 | — |

唯一索引：无（主键除外）。

### vip_order（会员订单）

[基线DDL第448行](../../backend/sql/zilvzi.sql#L448)；主键：id。

| 字段 | 类型 | 可空 | 键/关联 |
|---|---|---|---|
| id | bigint | 否 | PK |
| order_no | varchar(32) | 否 | — |
| user_id | bigint | 否 | F29 FK→user.id |
| price_fen | int | 否 | — |
| duration_days | int | 否 | — |
| payment_channel | varchar(32) | 否 | — |
| status | varchar(32) | 否 | — |
| create_time | datetime | 否 | — |
| confirmed_time | datetime | 是 | — |

唯一索引：uk_vip_order_no(order_no)。

## 验证范围

已检查21表/字段/PK/FK/唯一索引解析结果、正式增量合并条件、关系目标字段、基数和图件结构；独立复核重点约束。PNG/SVG与Mermaid均由同一结构清单生成，避免图与清单手工偏差。未连接实际MySQL、未导入基线、未运行迁移；不能据此断言部署库已应用这些脚本。源SQL的SHA256保存在结构清单中。
