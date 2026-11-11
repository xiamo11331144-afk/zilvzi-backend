from pathlib import Path
import re,json,hashlib,math
from html import escape
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).resolve().parent
BASE=ROOT/'backend/sql/zilvzi.sql'
SOURCES=[BASE,ROOT/'backend/sql/2026-06-28_checkin_task_fix.sql',ROOT/'backend/sql/2026-11-07_mentor_matching_rules.sql',ROOT/'backend/sql/2026-11-07_vip_membership.sql']
sql=BASE.read_text(encoding='utf-8-sig');tables={};rels=[]
for m in re.finditer(r'CREATE TABLE `([^`]+)`\s*\((.*?)\) ENGINE',sql,re.S):
 name,body=m.groups();t={'name':name,'line':sql[:m.start()].count('\n')+1,'columns':[],'pk':[],'unique':[],'fk':[]}
 for line in body.splitlines():
  c=re.match(r'\s*`([^`]+)`\s+([a-z]+(?:\([^)]*\))?)(.*)',line,re.I)
  if c:
   col,typ,tail=c.groups();t['columns'].append({'name':col,'type':typ,'nullable':'NOT NULL' not in tail,'definition':line.strip().rstrip(',')})
  if 'PRIMARY KEY' in line:t['pk']=re.findall(r'`([^`]+)`',line)
  u=re.search(r'UNIQUE INDEX `([^`]+)`\((.*?)\)',line)
  if u:t['unique'].append({'name':u[1],'columns':re.findall(r'`([^`]+)`',u[2])})
  fk=re.search(r'CONSTRAINT `([^`]+)` FOREIGN KEY \(`([^`]+)`\) REFERENCES `([^`]+)` \(`([^`]+)`\) ON DELETE (CASCADE|SET NULL|RESTRICT)',line)
  if fk:
   cn,cc,pt,pc,delete=fk.groups();r={'id':f'F{len(rels)+1:02}','kind':'FK','child':name,'column':cc,'parent':pt,'parent_column':pc,'constraint':cn,'on_delete':delete};t['fk'].append(r);rels.append(r)
 tables[name]=t
logical=[('ai_chat_history','user_id','user','id'),('checkin','task_id','plan_task','id'),('checkin','partner_id','user','id'),('notification','from_user_id','user','id')]
for i,(c,col,p,pc) in enumerate(logical,1):rels.append({'id':f'L{i:02}','kind':'LR','child':c,'column':col,'parent':p,'parent_column':pc})
for r in rels:
 t=tables[r['child']];c=next(c for c in t['columns'] if c['name']==r['column']);r['nullable']=c['nullable'];r['parent_cardinality']='0..1' if c['nullable'] else '1';unique=t['pk']==[r['column']] or any(u['columns']==[r['column']] for u in t['unique']);r['child_cardinality']='0..1' if unique else '0..N'
 # Logical association describes intended valid rows, never enforced existence.
 r['cardinality']=r['parent_cardinality']+' : '+r['child_cardinality']
assert len(tables)==21
# Verify every official migration's intended additions are already represented in this baseline.
checks=[('checkin','task_id'),('user','mentor_enabled'),('partner','match_mode')]
for t,c in checks:assert any(x['name']==c for x in tables[t]['columns'])
for t,idx in [('checkin','uk_task_date'),('checkin','idx_user_goal_date'),('checkin','idx_task_id'),('checkin','idx_mentor_activity'),('partner','idx_mentor_capacity')]:
 b=re.search(r'CREATE TABLE `'+t+r'`\s*\((.*?)\) ENGINE',sql,re.S)[1];assert '`'+idx+'`' in b
assert 'uk_user_goal_date' not in re.search(r'CREATE TABLE `checkin`\s*\((.*?)\) ENGINE',sql,re.S)[1]
assert tables['vip_membership']['pk']==['user_id']
(OUT/'schema-inventory.json').write_text(json.dumps({'scope':'baseline + official migrations, statically reconciled; not a live database inspection','tables':tables,'relationships':rels,'sources':[{'path':str(p.relative_to(ROOT)).replace('\\','/'),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in SOURCES]},ensure_ascii=False,indent=2),encoding='utf-8')
cn={'user':'用户','goal':'目标','plan':'计划','plan_task':'计划任务','checkin':'打卡记录','partner':'搭子关系','circle':'圈子','circle_member':'圈子成员','post':'动态','comment':'评论','like_record':'点赞','topic':'话题','topic_reply':'话题回复','achievement':'成就','ai_chat_history':'AI对话历史','file_upload':'上传记录','message':'私信','notification':'通知','plan_template':'方案模板','vip_membership':'会员有效期','vip_order':'会员订单'}
poly={'message':'POLY related_id + type（条件关联）','notification':'POLY related_id + related_type（多态）','file_upload':'POLY related_id + related_type（多态）'}
extra={'user':['username','phone','email','openid','mentor_enabled'],'goal':['name','type','status'],'plan':['name','status','completed_days'],'plan_task':['task_date','completed'],'checkin':['date','status'],'partner':['match_mode','status'],'circle':['name'],'circle_member':['role','status'],'post':['content'],'comment':['content'],'like_record':[],'topic':['title'],'topic_reply':['content'],'achievement':['medal_name'],'ai_chat_history':['session_type'],'file_upload':['file_name'],'message':['type'],'notification':['type'],'plan_template':['goal_type','goal_keyword','total_days'],'vip_membership':['vip_until'],'vip_order':['order_no','status','price_fen']}
# Mermaid full graph and every column, PK / FK / UK faithfully follow DDL; LR stays a comment label.
mmd=['erDiagram','    direction TB']
for n,t in tables.items():
 mmd.append('    '+n+' {')
 for c in t['columns']:
  tags=[]
  if c['name'] in t['pk']:tags.append('PK')
  if any(r['column']==c['name'] for r in t['fk']):tags.append('FK')
  # UK marks only single-column unique indexes. Composite constraints are listed separately.
  if any(u['columns']==[c['name']] for u in t['unique']):tags.append('UK')
  note='NULL' if c['nullable'] else 'NOT NULL'
  if any(r['kind']=='LR' and r['child']==n and r['column']==c['name'] for r in rels):note+='; LR'
  mmd.append(f'        {c["type"]} {c["name"]}'+(' '+', '.join(tags) if tags else '')+f' "{note}"')
 mmd.append('    }')
for r in rels:
 left='|o' if r['nullable'] else '||';right='o|' if r['child_cardinality']=='0..1' else 'o{';line='--' if r['kind']=='FK' else '..';mmd.append(f'    {r["parent"]} {left}{line}{right} {r["child"]} : "{r["id"]} {r["kind"]} {r["column"]}"')
mmd+=['%% Solid = physical FK; dotted = logical reference (LR), not enforced by database.','%% Line styles encode FK/LR here, not identifying/non-identifying relationship semantics.','%% Composite UNIQUE constraints and polymorphic references are documented in README.']
(OUT/'database-er.mmd').write_text('\n'.join(mmd)+'\n',encoding='utf-8')
F='C:/Windows/Fonts/msyh.ttc';B='C:/Windows/Fonts/msyhbd.ttc'
CW,CH=850,675
class Canvas:
 def __init__(self,w,h):
  self.w=w;self.h=h;self.im=Image.new('RGB',(w,h),'white');self.d=ImageDraw.Draw(self.im);self.s=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="100%" height="100%" fill="white"/>']
 def text(self,x,y,t,size=21,color='#20374b',bold=False,center=False,bg=None):
  f=ImageFont.truetype(B if bold else F,size)
  width=self.d.textlength(t,font=f)
  if center:x-=width/2
  if bg:
   self.d.rectangle((x-3,y-1,x+width+3,y+size+6),fill=bg)
   self.s.append(f'<rect x="{x-3}" y="{y-1}" width="{width+6}" height="{size+7}" fill="{bg}"/>')
  self.d.text((x,y),t,font=f,fill=color);self.s.append(f'<text x="{x}" y="{y+size}" font-family="Microsoft YaHei, sans-serif" font-size="{size}" fill="{color}" font-weight="{700 if bold else 400}">{escape(t)}</text>')
 def box(self,x,y,w,h,fill='#f5f8fb',color='#8b9db0'):
  self.d.rounded_rectangle((x,y,x+w,y+h),radius=11,fill=fill,outline=color,width=2);self.s.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="11" fill="{fill}" stroke="{color}" stroke-width="2"/>')
 def line(self,pts,dash=False,color='#526f89'):
  for a,b in zip(pts,pts[1:]):
   dist=math.dist(a,b)
   if dash:
    for q in range(0,int(dist),16):self.d.line([tuple(a[i]+(b[i]-a[i])*v/dist for i in (0,1)) for v in (q,min(q+9,dist))],fill=color,width=2)
   else:self.d.line([a,b],fill=color,width=2)
  self.s.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in pts)+f'" fill="none" stroke="{color}" stroke-width="2"'+(' stroke-dasharray="9 7"' if dash else '')+'/>')
 def save(self,name):
  self.im.save(OUT/(name+'.png'),dpi=(240,240));(OUT/(name+'.svg')).write_text('\n'.join(self.s+['</svg>']),encoding='utf-8')
def card(c,x,y,n):
 t=tables[n];rr=[r for r in rels if r['child']==n];c.box(x,y,CW-24,CH-25,fill='white',color='#bfccd8');c.text(x+22,y+14,cn[n]+'  /  '+n,27,bold=True)
 if rr:
  count=len(rr);pw=(CW-64-(count-1)*10)/count
  for i,r in enumerate(rr):
   px=x+25+i*(pw+10);cx=px+pw/2;blue='#3c7299' if r['kind']=='FK' else '#b07025';c.box(px,y+65,pw,78,fill='#edf5fb' if r['kind']=='FK' else '#fff6e8',color=blue);c.text(cx,y+76,r['parent'],20,bold=True,center=True);c.text(cx,y+108,'PK '+r['parent_column'],18,center=True)
   # Separate vertical lines retain a distinct column/role even for two references to user.
   c.line([(cx,y+143),(cx,y+237)],dash=r['kind']=='LR',color=blue);c.text(cx,y+151,r['id']+' '+r['cardinality'],18,color=blue,center=True,bg='white');c.text(cx,y+184,r['column'],18,color=blue,center=True,bg='white')
 else:
  c.text(x+30,y+90,'独立实体 / 无对外FK',23,color='#667b8d');c.text(x+30,y+135,'被引用关系在相应子表卡片中展开',21,color='#667b8d')
 cy=y+240;c.box(x+22,cy,CW-68,CH-295,fill='#f5f8fb')
 lines=[]
 for p in t['pk']:lines.append('PK   '+p+'  (NOT NULL)')
 for r in rr:
  tags=r['kind']+(' + PK' if r['column'] in t['pk'] else '');lines.append(tags+' '+r['column']+' → '+r['parent']+'.'+r['parent_column']+('  [NULL]' if r['nullable'] else '  [NN]'))
 for v in extra[n]:
  if v not in t['pk'] and not any(r['column']==v for r in rr):lines.append('      '+v)
 for u in t['unique']:lines.append('UQ  ('+', '.join(u['columns'])+')')
 if n in poly:lines.append(poly[n])
 if n=='plan_template':lines.append('无 template_id 持久关联到 plan')
 if n=='checkin':lines.append('UQ含NULL：不约束普通目标打卡重复')
 # Every PK and physical/logical FK is always visible; optional descriptive attributes may be compacted.
 available=CH-315;size=21;gap=min(32,available/max(len(lines),1))
 for i,line in enumerate(lines):c.text(x+38,cy+13+i*gap,line,size=20 if len(line)>62 else size)
 c.text(x+25,y+CH-52,f'SQL L{t["line"]} · '+str(len(t['columns']))+'字段；展示主键/关联/主要属性',18,color='#64798c')
def render(name,title,ns,cols):
 rows=math.ceil(len(ns)/cols);w=cols*CW+40;h=rows*CH+285;c=Canvas(w,h);c.text(w/2,22,title,37,bold=True,center=True);c.text(w/2,78,'基线 SQL + 正式增量 · 21 表全量模型 · 2026-11-07',23,center=True);c.text(w/2,120,'实线 FK = 数据库外键；虚线 LR = 逻辑关联；基数顺序：父实体 : 子实体',22,center=True)
 for i,n in enumerate(ns):card(c,30+(i%cols)*CW,175+(i//cols)*CH,n)
 c.text(w/2,h-96,'父实体小框是同名表的引用，不是新增实体；完整字段和所有关系见 Mermaid / 数据字典。',22,center=True)
 c.text(w/2,h-57,'LR基数是非空/可空字段的业务模型；未建立外键，数据库不保证目标存在。POLY不画成固定外键。',21,center=True)
 c.save(name)
core=['user','goal','plan','plan_task','checkin','partner'];social=['circle','circle_member','post','comment','like_record','topic','topic_reply'];support=['achievement','ai_chat_history','file_upload','message','notification','plan_template','vip_membership','vip_order']
render('database-er','数据库 E-R 总图（按子表展开，重复父框指向同一实体）',core+social+support,4)
render('er-core','E-R 分图：用户、目标、计划、任务、打卡与搭子',core,2)
render('er-social','E-R 分图：圈子、动态、话题与互动',social,2)
render('er-support','E-R 分图：成就、消息、AI、文件、模板与会员',support,2)
# Full data dictionary and evidence.
md=['# 数据库 E-R 图及结构核对','', '核对范围：当前基线DDL + README指定的3份正式增量脚本；未连接实际数据库。基线包含21表，'+str(sum(len(t['columns']) for t in tables.values()))+'字段，'+str(sum(r['kind']=='FK' for r in rels))+'个物理外键，另列4个明确逻辑关联。','', '## 图件与读法','', '- [总图PNG](./database-er.png) / [总图SVG](./database-er.svg)：覆盖21表。采用按子表展开的关系图布局，每个父实体小框只是同名表引用。', '- [核心业务PNG](./er-core.png) / [核心业务SVG](./er-core.svg)。','- [社交互动PNG](./er-social.png) / [社交互动SVG](./er-social.svg)。','- [支撑与会员PNG](./er-support.png) / [支撑与会员SVG](./er-support.svg)。','- [完整Mermaid E-R源码](./database-er.mmd)：包含全部字段及完整FK/LR连线，适合全局布局编辑。','- [结构清单JSON](./schema-inventory.json) / [生成脚本](./generate_er.py)。','', 'PK=主键；FK=DDL明确声明的外键；LR=有语义和使用依据但没有外键约束的逻辑引用；UQ=唯一索引（组合列共同唯一）；NN=NOT NULL；NULL=可空；POLY=根据类型解释目标ID的条件/多态引用。','', '数字基数按“父实体 : 子实体”读取：1 : 0..N表示每条子记录必须引用一条父记录，而一个父记录可以没有或有多条子记录；0..1 : 0..N表示子记录允许没有父记录。会员有效期为1 : 0..1：每个用户最多一条会员记录，用户可以没有会员记录。逻辑关联的基数只表示预期有效业务数据，不承诺数据库引用完整性。','', 'Mermaid中实线/虚线特意编码FK/LR，**不使用其线型表达标识/非标识关系**；端点乌鸦脚表达基数。图中省略部分普通属性，完整字段在下方数据字典和Mermaid中。','', '## SQL来源与增量合并','', '| 来源 | 核对结果 |','|---|---|', '[基线](../../backend/sql/zilvzi.sql) | 含当前21张表，已包含正式增量目标结构。', '[打卡增量](../../backend/sql/2026-06-28_checkin_task_fix.sql) | task_id、uk_task_date及查询索引已存在；旧uk_user_goal_date已不存在，现为普通idx_user_goal_date。', '[引领增量](../../backend/sql/2026-11-07_mentor_matching_rules.sql) | user.mentor_enabled、partner.match_mode及两个索引已在基线中。', '[VIP增量](../../backend/sql/2026-11-07_vip_membership.sql) | 两表与外键均已存在；CREATE IF NOT EXISTS不会覆盖基线表定义。', '', '[旧引领脚本](../../backend/sql/2026-11-06_mentor_matching.sql)仅为已被新脚本覆盖的旧迁移；不会在当前基线上再次无条件ADD同名字段。固定用户seed/cleanup脚本不作为正式结构迁移。合并为静态核对，未执行任何SQL。','', '## 完整关系清单','', '| ID | 子表字段 → 父表键 | 类别 | 父 : 子基数 | 删除行为 |','|---|---|---|---|---|']
for r in rels:md.append(f'| {r["id"]} | {r["child"]}.{r["column"]} → {r["parent"]}.{r["parent_column"]} | {r["kind"]} | {r["cardinality"]} | {r.get("on_delete","无数据库外键约束")} |')
md+=['', '除SET NULL的partner.goal_id、plan.goal_id、post.circle_id外，基线其余物理外键ON DELETE均为CASCADE；ON UPDATE均为RESTRICT。VIP增量省略ON UPDATE，但当前基线表显式为RESTRICT。','', '## 关键基数与约束边界','', '- user与vip_membership为1对0..1，因为vip_membership.user_id同时是PK与FK；不是1对多。vip_order与用户为多对1，未建立vip_order到vip_membership的外键。','- circle_member将用户和圈子展开为多对多，UNIQUE(circle_id,user_id)限制同一成员组合唯一。like_record将用户和动态展开为多对多，UNIQUE(user_id,post_id)限制同一点赞组合唯一。','- partner的user_id与partner_id都引用user.id，是两个角色的外键；UNIQUE(user_id,partner_id)只约束有序用户对，不能自动禁止反向重复或自身关联。checkin.partner_id实际是审核用户ID，不是partner关系表ID。','- goal到plan是1对0..N，plan.goal_id可空且不唯一；不能画成一目标只允许一个计划。task到checkin逻辑上也是1对0..N，UNIQUE(task_id,date)仅保证每个任务每日期最多一条，不是task_id全局唯一。','- checkin.task_id允许NULL。MySQL唯一索引允许多条含NULL的组合，因此uk_task_date和uk_user_task_date不能保证普通目标打卡每天只有一条。用户+目标+日期当前只有普通索引，应用层判重不等于数据库唯一约束。','- achievement的UNIQUE(user_id,medal_name)约束同用户同勋章名唯一，不表示每用户只有一个成就。user的username/phone/email/openid是分别可空的单列唯一索引，不是四列组合唯一。','- plan_template没有外键，plan没有template_id；goal_type、goal_keyword用于类型/关键词匹配，不能虚构plan_template到plan或goal的固定外键。','- file_upload.related_type/related_id和notification.related_type/related_id是通用类型+ID字段；DDL不能确定唯一目标表。message.related_id根据type解释，例如checkin_proof对应checkin.id。这些均不画成无条件物理外键，也不推定所有可能目标。','- DDL可表达每条记录引用哪个父记录，但不自动保证checkin.user_id与goal.user_id一致，或plan.user_id与goal.user_id一致；图中基数不代表这些跨表业务一致性已受约束。','', '## 逻辑关联依据','', '- L01 ai_chat_history.user_id→user.id：基线用户ID字段与索引，没有FK；[AiController](../../backend/src/main/java/com/zilvzi/controller/AiController.java)按登录userId存取历史。','- L02 checkin.task_id→plan_task.id：基线无FK；[今日任务SQL](../../backend/src/main/resources/mapper/CheckinMapper.xml#L25)按task_id连接，[打卡服务](../../backend/src/main/java/com/zilvzi/service/impl/CheckinServiceImpl.java#L84)按taskId读取任务。','- L03 checkin.partner_id→user.id：基线无FK；[审核SQL](../../backend/src/main/resources/mapper/CheckinMapper.xml#L101)写入审核用户ID，[待审目标查询](../../backend/src/main/resources/mapper/CheckinMapper.xml#L107)连接user。','- L04 notification.from_user_id→user.id：基线无FK且字段可空；[通知Mapper](../../backend/src/main/resources/mapper/NotificationMapper.xml)按用户关联发送者信息。','', '## 全量字段与唯一约束','']
for n,t in tables.items():
 md += [f'### {n}（{cn[n]}）','',f'[基线DDL第{t["line"]}行](../../backend/sql/zilvzi.sql#L{t["line"]})；主键：'+', '.join(t['pk'])+'。','', '| 字段 | 类型 | 可空 | 键/关联 |','|---|---|---|---|']
 for col in t['columns']:
  tags=['PK'] if col['name'] in t['pk'] else []
  tags += [r['id']+' '+r['kind']+'→'+r['parent']+'.'+r['parent_column'] for r in rels if r['child']==n and r['column']==col['name']]
  md.append(f'| {col["name"]} | {col["type"]} | {"是" if col["nullable"] else "否"} | {"；".join(tags) or "—"} |')
 md+=['','唯一索引：'+('；'.join(u['name']+'('+', '.join(u['columns'])+')' for u in t['unique']) or '无（主键除外）')+'。','']
md+=['## 验证范围','', '已检查21表/字段/PK/FK/唯一索引解析结果、正式增量合并条件、关系目标字段、基数和图件结构；独立复核重点约束。PNG/SVG与Mermaid均由同一结构清单生成，避免图与清单手工偏差。未连接实际MySQL、未导入基线、未运行迁移；不能据此断言部署库已应用这些脚本。源SQL的SHA256保存在结构清单中。']
(OUT/'README.md').write_text('\n'.join(md)+'\n',encoding='utf-8')
print(json.dumps({'tables':len(tables),'columns':sum(len(t['columns']) for t in tables.values()),'physical_foreign_keys':sum(r['kind']=='FK' for r in rels),'logical_references':4,'unique_indexes':sum(len(t['unique']) for t in tables.values())},ensure_ascii=False))
