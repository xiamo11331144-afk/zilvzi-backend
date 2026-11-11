# 传统实体—联系 E-R 图

按示例采用Chen式图形：矩形为实体，菱形为联系，椭圆为主要属性，下划线为主键。之前的表结构图保留，本目录提供符合实体—联系样式的新版本。

- [完整总图PNG](./chen-er-overview.png) / [矢量总图SVG](./chen-er-overview.svg)：按业务分区排布，同名实体表示同一实体。
- [核心业务PNG](./chen-core.png) / [SVG](./chen-core.svg)。
- [圈子与动态PNG](./chen-social.png) / [SVG](./chen-social.svg)。
- [话题与回复PNG](./chen-topic.png) / [SVG](./chen-topic.svg)。
- [成就、AI、文件与会员PNG](./chen-support.png) / [SVG](./chen-support.svg)。
- [私信、通知与模板PNG](./chen-message.png) / [SVG](./chen-message.svg)。
- [生成脚本](./generate_chen.py)，每张分图的layout.json保存可编辑坐标、实体和联系数据；SVG中的图形和文字可单独编辑。

## 结构依据与符号

沿用[SQL结构清单](../database-er-20261107/schema-inventory.json)及[全量字段、外键和唯一索引依据](../database-er-20261107/README.md)，生成时重新核对4份源SQL的SHA256。当前模型21实体、29个物理FK联系、4个确定的逻辑联系，完整覆盖全部33条固定关联。实体主要属性是从189个字段中选取，非全部字段。外键字段主要通过联系菱形表达，不再作为椭圆重复铺满图面。

实线：DDL物理外键；虚线：逻辑关联，数据库未强制目标存在。菱形F01等编号与原结构清单一致。联系使用有业务含义的中文名称；搭子关系的申请方/接收方、私信的发送/接收分别绘制，避免混淆角色。

图旁使用1、0..1、0..n表达实际可选基数，比只写1:n更严格。用户可没有会员有效期记录，最多一条，因此是1:0..1。checkin.task_id可空且仅(task_id,date)组合唯一，所以任务与打卡是0..1:0..n，不能画成全局1:1。

搭子关系、圈子成员、点赞记录有自己的主键和状态/时间等属性，因此保留为关联实体，并用菱形连接各参与实体；没有再次添加重复的m:n关系。用户—圈子经圈子成员体现多对多，用户—动态经点赞记录体现多对多，用户—用户经搭子关系体现多对多。

notification、file_upload的related_type/related_id以及message.related_id为条件/多态引用，未伪造固定目标表联系。plan_template没有到plan的持久外键，保持独立实体。没有数据库DDL依据的回复自关联、模板关联不绘制。

仅重绘文档，未修改业务、SQL或实际数据库。检查包括源SQL哈希一致性、21实体与33条关系覆盖、选取属性存在性、主键下划线及SVG/PNG格式。未连接数据库进行部署结构验收。
