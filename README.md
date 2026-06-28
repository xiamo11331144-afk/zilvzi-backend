# 自律打卡搭子 — 微信小程序

> 基于 Spring Boot + MyBatis + MySQL + Redis + JWT 的习惯打卡与搭子互助平台

## 项目简介

一款面向习惯养成场景的微信小程序，核心功能是**打卡**与**搭子互助**。用户可以创建学习、运动、阅读等目标计划，系统自动生成每日任务；支持与志同道合的搭子结伴，互相审核打卡凭证，通过社交压力促进习惯坚持。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | 微信小程序原生（WXML / WXSS / JS） |
| 后端 | Spring Boot 3.2.5 + MyBatis |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis（JWT Token / 会话管理） |
| 认证 | JWT + 微信登录（openid） |
| 文件存储 | 阿里云 OSS |
| AI 对话 | 集成大模型 API 的智能助手 |

## 核心功能

### 打卡系统
- 按计划自动生成每日任务，支持断卡检测与自动补发
- 有搭子时需上传凭证（照片+文字），由搭子审核通过后才算完成
- 无搭子时一键打卡，兼容自打卡场景

### 搭子互助
- 智能匹配算法：按目标类型 × 打卡率 × 地域 × 等级综合评分
- 共勉型 / 引领型两种匹配模式
- 拉黑、解除、提醒打卡等完整社交功能

### 其他模块
- **成就勋章**：12 种勋章，连续打卡 / 社交 / 首次打卡等触发
- **圈子社交**：动态发布、评论、点赞
- **私信聊天**：文字 + 打卡凭证消息，支持实时审核
- **AI 助手**：浮窗对话，记录习惯相关问答
- **排行榜**：按打卡天数、连续天数排名
- **数据统计**：打卡趋势、目标完成率

## 项目结构

```
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/zilvzi/
│   │   ├── controller/         # REST API（12 个）
│   │   ├── service/            # 业务逻辑层
│   │   ├── mapper/             # MyBatis 数据访问层
│   │   └── pojo/               # 实体类
│   └── src/main/resources/
│       ├── mapper/             # SQL 映射文件
│       └── application.yml     # 配置文件
│
└── miniprogram/                # 微信小程序前端
    ├── pages/
    │   ├── index/              # 首页（今日任务）
    │   ├── partner/            # 搭子（匹配/管理）
    │   ├── message/            # 私信聊天
    │   ├── profile/            # 个人中心
    │   ├── circle/             # 圈子社交
    │   ├── checkin/            # 打卡日历
    │   └── achievement/        # 成就勋章
    └── utils/                  # 请求封装、工具函数
```

## 数据库设计（核心表）

| 表名 | 说明 |
|------|------|
| user | 用户信息（含地域、等级、经验） |
| goal | 用户目标（学习/运动/阅读等类型） |
| plan | 计划（关联目标，含周期配置） |
| plan_task | 每日任务（按计划自动生成） |
| checkin | 打卡记录（含凭证审核流程） |
| partner | 搭子关系（申请/接受/拉黑） |
| message | 私信消息（文字/打卡凭证） |
| achievement | 用户勋章 |
| circle_post | 圈子动态 |
| ai_chat_history | AI 对话记录 |

## 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- 微信开发者工具
- Maven 3.8+

## 快速启动

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE zilvzi DEFAULT CHARACTER SET utf8mb4;"

# 2. 导入表结构
mysql -u root -p zilvzi < backend/sql/schema.sql

# 3. 修改配置
# 编辑 backend/src/main/resources/application.yml
# 填入 MySQL、Redis、JWT Secret、微信 AppID/Secret

# 4. 启动后端
cd backend
mvn spring-boot:run

# 5. 前端
# 用微信开发者工具打开 miniprogram/ 目录
# 配置 appid，指向后端地址
```