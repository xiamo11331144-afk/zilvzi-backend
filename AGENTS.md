# 工作区开发指南

本工作区是“自律搭子监督与习惯养成平台”，主要采用前后端分离架构。

## 技术栈与事实来源

- 后端：`backend/`，Java 17、Spring Boot 3.2.5、Maven、MyBatis XML、MySQL、Redis、JWT；依赖以 `backend/pom.xml` 为准。
- 主前端：`miniprogram/`，微信原生小程序 JS、WXML、WXSS、JSON；不是 React/Vue 项目。
- 后台管理：根据 `README.md`，服务入口为 `/admin`；修改前查找后端资源目录中的实际页面与接口。
- 启动说明：`README.md`。数据库基线与升级脚本：`backend/sql/`。
- 已观察到请求封装 `miniprogram/utils/request.js`，响应类 `backend/src/main/java/com/zilvzi/pojo/Result.java`；其他行为必须阅读当前源码确认。

## 工作区 skills

以下是项目本地技能，位于 `.agents/skills/`。开始匹配任务前，先读取对应 `SKILL.md`，再执行；跨层任务可组合使用。若工具的技能列表尚未显示这些本地技能，直接通过文件读取，不要声称已被运行时注册。

| 场景 | 应读取的技能 |
| --- | --- |
| 分析项目、梳理架构、了解业务、评估功能影响 | `.agents/skills/project-analysis/SKILL.md` |
| 修改 Java 接口、业务服务、MyBatis、SQL、Redis、鉴权 | `.agents/skills/spring-boot-backend/SKILL.md` |
| 修改微信小程序页面、交互、样式、请求和登录态 | `.agents/skills/wechat-miniprogram-frontend/SKILL.md` |
| 前后端联调、新增跨层功能、调整接口字段与协议 | `.agents/skills/api-integration/SKILL.md` |
| 排查报错、复现问题、修复回归、验证改动 | `.agents/skills/debugging-verification/SKILL.md` |

组合顺序：先按需分析定位，再读取涉及层的开发技能；跨层修改增加联调技能；发生故障增加排查技能。只加载与当前任务相关的文件。

`.opencode/skills/` 中已有 `frontend-design` 和 `brainstorming`，保持原样。只有用户明确调用，或任务实际符合其适用范围时才加载；不要因为它们存在就套用到全部任务。微信小程序的视觉实现必须遵守小程序能力和项目现有风格。

## 执行约定

1. 默认中文沟通。用户要求修改功能时，先阅读当前实现，再完成必要的修改与验证；不只输出建议。
2. 先检查已有用户改动（有 Git 时使用 `git status`），不覆盖、回退或提交无关改动；未要求时不自动提交或推送。
3. 明确且可逆的改动直接推进。仅在缺失信息会影响核心行为、权限边界或数据安全时，提出具体问题；不要对普通编辑反复要求确认。
4. 保持现有结构、命名与接口兼容，不顺带换框架、升级依赖或做大范围重构。修改源码，不编辑 `backend/target/` 生成物。
5. 不输出配置里的数据库密码、JWT 密钥、第三方凭据或真实令牌。配置排查只报告字段名与是否已配置。
6. 数据库变更优先提供独立、兼容现有数据的增量脚本；不自动重置库或向实际数据库执行破坏性 SQL。
7. 检查须与改动匹配。区分语法检查、编译、自动测试、接口实测与人工验收；没有执行的检查明确说明，不能把编译成功称为功能已验证。
8. 输出重点：完成内容、关键文件、验证结果、尚存的环境限制。分析结论标明源码依据；推测和待验证项单独说明。
