# 全项目最终扫描报告

扫描日期：2026-11-10。以下保留扫描当时的结论、源码位置及复现结果；12项发现的代码修复已完成，当前结果见[修复记录](<final-project-fixes-2026-11-10.md>)。历史行号可能随修复变化。原始结论：现有自动回归通过，但仍有权限、内容渲染、关系状态及数据口径问题，应在正式发布前处理。测试通过不代表本报告列出的路径已被覆盖。

## 范围与执行方式

- 扫描后端104个Java源码、20个Mapper XML、8个SQL文件；覆盖登录与管理员鉴权、用户、目标、计划、打卡及审核、搭子、私信、通知、圈子/话题、排行榜、会员、上传、导出和管理后台。
- 小程序检查24个JS、22个JSON、17个注册页面及4个tabBar；核对主要请求路径、字段、登录态、上传和导航。页面注册数量以当前配置为准。
- 本次只做审查、自动回归及隔离复核，未修改应用源码、提交代码或向业务数据库写入数据。新增的复核脚本均在验证目录。
- 未启动真实HTTP联调或微信开发者工具；未验证实际浏览器脚本执行、真实并发、外部短信/邮件、微信、OSS、AI或真实支付。没有执行网络依赖漏洞扫描。

## 验证结果

| 检查 | 结果 | 边界 |
| --- | --- | --- |
| Java编译与Maven全量自动测试 | 140项，132通过，8跳过，0失败/错误 | 使用Java17离线隔离构建；8项MySQL集成测试关闭，未连接业务库 |
| 小程序及通知Node回归 | 40通过，0失败/跳过 | 覆盖现有打卡、会员、引领状态、通知等；为模拟环境 |
| JS/JSON与页面注册检查 | 24 JS、22 JSON、17页面无检查错误 | 不等于WXML/WXSS实际渲染或微信编译 |
| Mapper语句名与接口方法对应 | 20个XML无该项检查错误 | 不是所有SQL的真实数据库执行验证；未发现`${}`直接拼接 |
| 后台渲染隔离复核 | 用户内容仍作为HTML保留 | 使用无害标记验证字符串输出；未执行真实浏览器攻击 |
| 登录失败隔离复核 | 两个业务错误处理问题均复现 | 模拟HTTP200内业务code500，无真实请求 |
| 导出隔离复核 | CSV错列、PDF无分页现象复现 | 40条模拟任务，Mock响应，无数据库访问 |

## 高优先级问题

### 1. P1：后台用户内容未正确转义，存在存储型XSS风险

依据：[后台页面:206](<C:/Users/36506/Desktop/毕业设计/backend/src/main/resources/static/admin/index.html#L206>)、[用户列表输出:212](<C:/Users/36506/Desktop/毕业设计/backend/src/main/resources/static/admin/index.html#L212>)、[列表写入DOM:226](<C:/Users/36506/Desktop/毕业设计/backend/src/main/resources/static/admin/index.html#L226>)。

`esc`只处理部分引号，不处理HTML中的`<`、`>`、`&`，昵称等内容经字符串拼接交给`innerHTML`；部分字段直接输出。圈子、帖子、评论和话题列表也存在同类输出方式。隔离渲染已确认输入的无害HTML标记被当作标记保留。

触发及影响：普通用户提交带HTML事件属性的内容，管理员打开相关列表时，可能在后台来源下执行脚本并访问后台令牌或调用管理接口。实际浏览器执行尚未测试。

建议：文本用`textContent`写入，事件用监听器绑定；移除含用户数据的内联事件。若必须生成HTML，按HTML文本、属性、脚本各自上下文处理，不能用一个简单替换函数通用转义。

### 2. P1：管理员降级或删除后，旧后台JWT仍能继续使用

依据：[后台拦截器:41](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/interceptor/AdminInterceptor.java#L41>)、[JWT管理员声明校验:113](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/utils/JwtUtil.java#L113>)、[后台角色更新:94](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/controller/AdminController.java#L94>)。

后台鉴权只验证签名、时效和令牌中的管理员声明，不检查用户是否仍存在或是否仍为管理员。已登录管理员被降级或删除后，原令牌仍会通过该校验，直至过期；这是源码确认，未执行真实HTTP撤权测试。

建议：后台请求校验实时账户角色，或使用可失效的会话/令牌版本并在撤权时更新。补充“先登录→撤权→旧令牌写操作被拒绝”的回归。

## 业务与交互问题

### 3. P2：拉黑后仍可发送私信和打卡提醒

依据：[私信发送:37](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/controller/MessageController.java#L37>)、[打卡提醒:309](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/PartnerServiceImpl.java#L309>)。

私信仅检查给自己发送、空内容及敏感词，没有检查双向拉黑。提醒直接按传入用户ID发送通知，也未要求存在有效搭子关系。因此B拉黑A后，A仍可通过接口向B发消息或提醒。未进行实际账户联调。

建议：在服务层统一检查双向blocked；提醒还须校验双方存在允许提醒的有效搭子关系。测试应覆盖两个方向与直接调用接口。

### 4. P2：重复拉黑再解除，会丢失原普通搭子关系

依据：[拉黑状态写入:359](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/PartnerServiceImpl.java#L359>)、[解除拉黑:380](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/PartnerServiceImpl.java#L380>)。

首次拉黑将`accepted`保存到`prev_status`；第二次拉黑把`prev_status`改成`blocked`。解除时只恢复`accepted/pending`，其他值删除记录。因此原普通搭子关系可能丢失。现有用户锁不解决顺序重复调用的幂等问题。

建议：已经blocked时幂等返回，不覆盖原状态；增加accepted→block→block→unblock回归。该结论为源码路径推演，未写数据库复现。

### 5. P2：同一阶段首次生成任务存在并发重复插入窗口

依据：[阶段任务生成:239](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/PlanServiceImpl.java#L239>)、[阶段任务查询:57](<C:/Users/36506/Desktop/毕业设计/backend/src/main/resources/mapper/PlanTaskMapper.xml#L57>)、[批量插入:34](<C:/Users/36506/Desktop/毕业设计/backend/src/main/resources/mapper/PlanTaskMapper.xml#L34>)、[任务表约束:297](<C:/Users/36506/Desktop/毕业设计/backend/sql/zilvzi.sql#L297>)。

事务中普通查询是否已有阶段任务，查空后调用AI再插入；没有计划行锁、原子生成标记或对应业务唯一约束。新计划无旧任务时，自动标记断卡逻辑也不会提供计划级锁。两个请求可能同时查空并各自插入一整批任务。

影响为重复任务、进度膨胀及重复AI调用。属于静态确认的并发窗口，未进行真实并发复现。建议按计划锁定并在锁内重查，结合阶段生成状态与适当唯一约束；避免将整个外部AI等待简单放入长事务。

### 6. P2：话题编辑绕过创建时的敏感词检查

依据：[创建时检查:38](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/TopicServiceImpl.java#L38>)、[编辑直接更新:110](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/TopicServiceImpl.java#L110>)。

创建及回复检查敏感词，编辑仅检查作者后直接更新标题、内容，允许先创建合法内容再编辑成应拦截内容。编辑还接受`isPinned`；普通作者能否置顶需要明确业务政策，本报告不将其单独判为越权。

建议：创建和编辑复用相同的内容校验；按角色限制可编辑字段。该项为源码确认，未执行HTTP编辑测试。

### 7. P2：排行榜计入待审核及被拒绝的打卡

依据：[排行榜SQL:87](<C:/Users/36506/Desktop/毕业设计/backend/src/main/resources/mapper/UserMapper.xml#L87>)。

排行榜总天数的关联及连续天数子查询都没有限定`status='approved'`；pending/rejected日期也可计入。该结果与当前打卡审核通过后生效、引领资格只认有效打卡的口径不一致。

建议：总天数和连续天数均统一为审核通过的不同日期，并测试同日多任务、待审核、拒绝后通过和断签边界。尚未使用真实数据库对比排名。

### 8. P2：登录页未处理HTTP200内的业务失败

依据：[发送验证码:117](<C:/Users/36506/Desktop/毕业设计/miniprogram/pages/login/login.js#L117>)、[登录与注册反馈:191](<C:/Users/36506/Desktop/毕业设计/miniprogram/pages/login/login.js#L191>)、[请求封装:62](<C:/Users/36506/Desktop/毕业设计/miniprogram/utils/request.js#L62>)。

请求封装对HTTP200直接resolve；后端业务失败会返回业务code500。验证码发送未检查code，失败也提示“验证码已发送”并开始60秒冷却；普通登录/注册仅处理code200，业务失败时没有错误提示。

两条路径均用模拟code500复现。建议页面明确处理失败消息，再决定是否进入冷却/保存登录态；若统一调整请求封装，应检查现有调用的兼容性。

### 9. P2：微信会话响应及验证码写入日志

依据：[微信响应日志:157](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/UserServiceImpl.java#L157>)、[验证码日志:214](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/UserServiceImpl.java#L214>)。

代码输出微信会话接口完整响应（成功响应可含`session_key`），并输出目标地址及验证码。仅检查源码，未读取任何实际凭据或真实响应。

建议：去掉响应原文和验证码日志，只保留脱敏结果、错误码及追踪标识。当前验证码发送也只有Redis保存和控制台输出，短信/邮件提供方仍是TODO；现有说明将其定位为演示功能，正式接入前不能视为已完成真实投递。

### 10. P2：CSV未转义，任务名称含逗号会导致错列

依据：[CSV输出:23](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/ExportServiceImpl.java#L23>)。

直接以逗号拼接字段，没有处理逗号、换行和双引号。模拟任务名`read,write`已复现预期5列变成6列。用户文本的电子表格公式前缀也未处理，其实际执行取决于打开软件及设置，未测试。

建议：按CSV规则加引号并双写字段内引号，明确公式文本策略及Excel兼容编码，增加逗号/引号/多行字段导出测试。

### 11. P2：PDF无分页，字体/异常回退也存在格式风险

依据：[PDF导出:49](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/service/impl/ExportServiceImpl.java#L49>)。

仅创建一个页面，所有任务以固定24点行距持续输出，没有高度检查。40条模拟任务仍输出1页；结合起始坐标750与标题占行，后部文字会超出页面。未进行PDF图片渲染验收。

字体优先依赖Windows路径；异常时直接调用CSV导出，因此PDF接口可能返回CSV格式。跨操作系统失败分支未实际执行。

建议：按页面剩余高度分页并处理长文本，打包可用中文字体；PDF失败应返回明确错误，不能静默换成另一种文件格式。

### 12. P2：管理后台API地址写死为访问者本机

依据：[后台API基址:139](<C:/Users/36506/Desktop/毕业设计/backend/src/main/resources/static/admin/index.html#L139>)。

固定`http://localhost:8081`。本机默认端口访问可用，但从其他电脑打开已部署后台时，会请求访问者自己的8081端口；更换端口或HTTPS也会产生问题。

建议：后台与接口同源时使用相对地址，否则通过部署配置注入。已做源码及静态检查，未进行远端浏览器访问测试。

## 其他交付注意事项

- [后台统计:128](<C:/Users/36506/Desktop/毕业设计/backend/src/main/java/com/zilvzi/controller/AdminController.java#L128>)中的日活、打卡率目前固定为0，不能当真实统计指标验收。
- [启动说明](<C:/Users/36506/Desktop/毕业设计/README.md>)未说明新加入的[历史审核任务修复脚本](<C:/Users/36506/Desktop/毕业设计/backend/sql/2026-11-10_checkin_review_completion.sql>)。该脚本是有条件的历史数据修复，补文档时应说明备份、适用范围和人工审核，不能将其无条件自动执行。
- 小程序开发环境地址及生产域名占位配置需要发布前切换；会员真实支付未接入，现有UI有说明。外部能力的真实可用性仍需在目标环境验收。
- 核心对象归属检查、打卡审核的锁与状态更新、引领申请/接受的容量锁、会员模拟确认的环境限制及幂等保护，在所检查路径中已有实现；本报告没有将这些路径泛化为“完全无风险”。

## 建议处理顺序

先处理后台XSS和管理员撤权；随后处理拉黑拦截/幂等、登录失败反馈、排行榜审核口径、话题编辑校验；再处理计划生成并发及导出；最后补部署配置、日志脱敏和说明文档。每项修复补充能覆盖上述触发条件的回归，而不是重复运行现有测试后宣称问题已消失。

## 本次复核脚本

- [静态项目扫描](<C:/Users/36506/Desktop/毕业设计/本地归档_项目杂项_20261111/验证缓存/.verification/final-project-scan.cjs>)：JS/JSON语法、注册页面文件、Mapper接口名称及后台渲染标记。
- [登录失败复核](<C:/Users/36506/Desktop/毕业设计/本地归档_项目杂项_20261111/验证缓存/.verification/login-readonly-probe.cjs>)：模拟业务code500，验证错误成功提示与静默失败。
- [导出复核](<C:/Users/36506/Desktop/毕业设计/本地归档_项目杂项_20261111/验证缓存/.verification/ExportReadOnlyProbe.java>)：Mockito数据及Mock响应验证CSV列数和PDF页数；运行需使用隔离构建的应用类与测试依赖类路径。
