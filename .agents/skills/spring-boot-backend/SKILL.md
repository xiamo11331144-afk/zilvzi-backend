---
name: spring-boot-backend
description: 在本工作区实现或修改 Spring Boot Java 后端接口和业务，包括 Controller、Service、MyBatis Mapper/XML、MySQL、Redis、JWT、校验与异常处理。后端任务开始前读取。
---

# Spring Boot 后端开发

## 项目约束
- 以 `backend/pom.xml` 和源码为准：Java 17、Spring Boot 3、MyBatis XML、MySQL、Redis、JWT；不假设项目使用 JPA、WebFlux 或 Lombok 之外的未配置框架。
- 遵循现有分层与命名。先查相似业务的 Controller/Service/Mapper/XML/POJO 及配置，再决定改动点。
- 响应主要使用 `com.zilvzi.pojo.Result`，但须核实具体端点和全局异常处理的现行约定，不能机械套用。

## 修改流程
1. 查明对应端点、请求 DTO/参数、权限拦截器、服务调用、Mapper SQL 和表字段。
2. 明确兼容性：HTTP 方法/路径、字段名/类型、分页形状、响应 `code/message/data` 和错误码；保持现有调用方可用。
3. 在正确层实施校验与业务规则。数据写操作审视 `@Transactional`、幂等性、并发和唯一约束；查询 SQL 限制范围并添加合适排序/分页。
4. MyBatis XML 与接口方法参数保持一致；检查 `#{}` 参数绑定，避免用 `${}` 拼接用户输入。数据库变更创建独立、可重复审阅的增量 SQL，不执行破坏性迁移。
5. 权限按服务/路由实际规则处理，不能信任客户端传入的用户 ID 来代替认证主体。日志不得记录密码、令牌或密钥。
6. 添加针对业务分支的测试，优先复用现有测试栈与模式。

## 验证
- 至少运行后端测试或 Maven 编译（例如 `mvn test` / `mvn -DskipTests package`，在 `backend/` 执行）。
- 报告命令、结果和未覆盖部分。测试失败先判断是否由本次改动引起；不连接或改写真实数据库，除非用户明确要求且环境安全已确认。
- 不编辑 `backend/target/`；它是构建输出。
