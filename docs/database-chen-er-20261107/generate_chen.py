from pathlib import Path
import json,math,hashlib,xml.etree.ElementTree as ET,zipfile
from html import escape
from PIL import Image,ImageDraw,ImageFont
OUT=Path(__file__).resolve().parent;ROOT=OUT.parents[1]
SRC=ROOT/'docs/database-er-20261107/schema-inventory.json';model=json.loads(SRC.read_text(encoding='utf-8'));tables=model['tables'];rels=model['relationships']
for s in model['sources']:assert hashlib.sha256((ROOT/s['path']).read_bytes()).hexdigest()==s['sha256'],'SQL changed: '+s['path']
N={'user':'用户','goal':'目标','plan':'计划','plan_task':'计划任务','checkin':'打卡记录','partner':'搭子关系','circle':'圈子','circle_member':'圈子成员','post':'动态','comment':'评论','like_record':'点赞记录','topic':'话题','topic_reply':'话题回复','achievement':'成就','ai_chat_history':'AI对话历史','file_upload':'上传记录','message':'私信','notification':'通知','plan_template':'方案模板','vip_membership':'会员有效期','vip_order':'会员订单'}
A={'user':[('id','用户编号'),('nickname','昵称'),('level','等级'),('mentor_enabled','引领开关')],'goal':[('id','目标编号'),('name','目标名称'),('type','目标类别'),('status','状态')],'plan':[('id','计划编号'),('name','计划名称'),('total_days','计划天数'),('status','状态')],'plan_task':[('id','任务编号'),('task_date','任务日期'),('task_name','任务名称'),('completed','完成标志')],'checkin':[('id','打卡编号'),('date','打卡日期'),('status','审核状态'),('proof_image','凭证图片')],'partner':[('id','关系编号'),('match_mode','匹配模式'),('status','关系状态'),('create_time','创建时间')],'circle':[('id','圈子编号'),('name','圈子名称'),('description','圈子简介')],'circle_member':[('id','成员记录编号'),('role','成员角色'),('status','加入状态')],'post':[('id','动态编号'),('content','动态内容'),('images','图片')],'comment':[('id','评论编号'),('content','评论内容'),('create_time','评论时间')],'like_record':[('id','点赞编号'),('create_time','点赞时间')],'topic':[('id','话题编号'),('title','话题标题'),('content','话题内容')],'topic_reply':[('id','回复编号'),('content','回复内容'),('create_time','回复时间')],'achievement':[('id','成就编号'),('medal_name','勋章名称'),('unlock_time','解锁时间')],'ai_chat_history':[('id','消息编号'),('role','消息角色'),('session_type','会话类型')],'file_upload':[('id','文件编号'),('file_name','文件名称'),('related_type','关联类型')],'message':[('id','私信编号'),('type','私信类型'),('content','私信内容')],'notification':[('id','通知编号'),('type','通知类型'),('related_type','关联类型')],'plan_template':[('id','模板编号'),('goal_type','目标类别'),('goal_keyword','目标关键词')],'vip_membership':[('user_id','用户编号'),('vip_until','会员到期时间')],'vip_order':[('id','订单编号'),('order_no','订单号'),('status','订单状态')]}
verbs={'achievement.user_id':'解锁','ai_chat_history.user_id':'保存对话','checkin.user_id':'提交','checkin.goal_id':'记录','checkin.task_id':'对应任务','checkin.partner_id':'审核','circle.creator_id':'创建','circle_member.circle_id':'包含成员','circle_member.user_id':'加入圈子','comment.user_id':'发表评论','comment.post_id':'收到评论','file_upload.user_id':'上传','goal.user_id':'设定','like_record.user_id':'执行点赞','like_record.post_id':'获得点赞','message.from_user_id':'发送','message.to_user_id':'接收','notification.user_id':'接收通知','notification.from_user_id':'发起通知','partner.user_id':'申请方','partner.partner_id':'接收方','partner.goal_id':'关联目标','plan.user_id':'制定','plan.goal_id':'关联计划','plan_task.plan_id':'包含任务','post.user_id':'发布动态','post.circle_id':'收录动态','topic.circle_id':'设有话题','topic.user_id':'发起话题','topic_reply.topic_id':'包含回复','topic_reply.user_id':'发表回复','vip_membership.user_id':'拥有会员','vip_order.user_id':'创建订单'}
FONT='C:/Windows/Fonts/msyh.ttc';BOLD='C:/Windows/Fonts/msyhbd.ttc'
class C:
 def __init__(self,w,h):
  self.w=w;self.h=h;self.im=Image.new('RGB',(w,h),'white');self.d=ImageDraw.Draw(self.im);self.svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="100%" height="100%" fill="white"/>'];self.nodes=[];self.edges=[]
 def text(self,x,y,s,size=28,bold=False,underline=False,bg=False):
  f=ImageFont.truetype(BOLD if bold else FONT,size);w=self.d.textlength(s,font=f);x-=w/2;y-=size/2
  if bg:self.d.rectangle((x-8,y-3,x+w+8,y+size+11),fill='white');self.svg.append(f'<rect x="{x-8}" y="{y-3}" width="{w+16}" height="{size+14}" fill="white"/>')
  self.d.text((x,y),s,font=f,fill='#171717');self.svg.append(f'<text x="{x+w/2}" y="{y+size}" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}"'+(' text-decoration="underline"' if underline else '')+'>'+escape(s)+'</text>')
  if underline:self.d.line((x,y+size+6,x+w,y+size+6),fill='#171717',width=2)
 def line(self,pts,dash=False):
  for a,b in zip(pts,pts[1:]):
   dist=math.dist(a,b)
   if dash and dist:
    for q in range(0,int(dist),22):self.d.line([tuple(a[i]+(b[i]-a[i])*v/dist for i in (0,1)) for v in (q,min(q+13,dist))],fill='#333333',width=3)
   else:self.d.line([a,b],fill='#333333',width=3)
  self.svg.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in pts)+'" fill="none" stroke="#333" stroke-width="3"'+(' stroke-dasharray="13 9"' if dash else '')+'/>')
 def shape(self,kind,cx,cy,w,h):
  box=(cx-w/2,cy-h/2,cx+w/2,cy+h/2)
  if kind=='rect':self.d.rectangle(box,fill='white',outline='#222',width=3);self.svg.append(f'<rect x="{box[0]}" y="{box[1]}" width="{w}" height="{h}" fill="white" stroke="#222" stroke-width="3"/>')
  elif kind=='ellipse':self.d.ellipse(box,fill='white',outline='#333',width=3);self.svg.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{w/2}" ry="{h/2}" fill="white" stroke="#333" stroke-width="3"/>')
  else:
   pts=[(cx-w/2,cy),(cx,cy-h/2),(cx+w/2,cy),(cx,cy+h/2)];self.d.polygon(pts,fill='white',outline='#222',width=3);self.svg.append('<polygon points="'+' '.join(f'{x},{y}' for x,y in pts)+'" fill="white" stroke="#222" stroke-width="3"/>')
 def save(self,name):
  self.im.save(OUT/(name+'.png'),dpi=(240,240));(OUT/(name+'.svg')).write_text('\n'.join(self.svg+['</svg>']),encoding='utf-8');(OUT/(name+'.layout.json')).write_text(json.dumps({'width':self.w,'height':self.h,'entities':self.nodes,'relationships':self.edges},ensure_ascii=False,indent=2),encoding='utf-8')
EW,EH=300,110
# Attributes above/below entities; FK columns are represented by relationship diamonds.
def entity(c,n,pos,attributes=True):
 x,y=pos
 if attributes:
  attrs=A[n];top=attrs[:2];bottom=attrs[2:]
  for group,dy in [(top,-210),(bottom,210)]:
   for i,(col,label) in enumerate(group):
    ax=x+((i-(len(group)-1)/2)*260 if len(group)>1 else 270);ay=y+dy
    if getattr(c,'support_mode',False):
     if n=='user':ax=x+(-440 if i==0 else 440);ay=y+(-120 if dy<0 else 120)
     elif n=='achievement' and dy>0:ax=x-270
     elif n in ('vip_membership','vip_order'):
      idx=attrs.index((col,label));ax=x+(idx-(len(attrs)-1)/2)*270;ay=y+230
    c.line([(x,y),(ax,ay)]);c.shape('ellipse',ax,ay,230,82);c.text(ax,ay,label,26,underline=col in tables[n]['pk'])
 c.shape('rect',x,y,EW,EH);c.text(x,y-14,N[n],30,bold=True);c.text(x,y+26,n,21)
 c.nodes.append({'table':n,'x':x,'y':y,'attributes':A[n] if attributes else []})
def anchor(a,b):
 dx,dy=b[0]-a[0],b[1]-a[1];k=min(EW/2/abs(dx) if dx else 1e9,EH/2/abs(dy) if dy else 1e9);return (a[0]+dx*k,a[1]+dy*k)
def pathmid(pts,frac):
 lengths=[math.dist(a,b) for a,b in zip(pts,pts[1:])];target=sum(lengths)*frac
 for a,b,l in zip(pts,pts[1:],lengths):
  if target<=l:return (a[0]+(b[0]-a[0])*target/l,a[1]+(b[1]-a[1])*target/l)
  target-=l
 return pts[-1]
def relation(c,r,positions,bends=None,frac=.5):
 a=positions[r['parent']];b=positions[r['child']];pts=[a]+(bends or [])+[b];pts[0]=anchor(a,pts[1]);pts[-1]=anchor(b,pts[-2]);c.line(pts,r['kind']=='LR');dx,dy=pathmid(pts,frac);verb=verbs[r['child']+'.'+r['column']];c.shape('diamond',dx,dy,230,142);c.text(dx,dy-12,verb,25);c.text(dx,dy+26,r['id']+(' 逻辑' if r['kind']=='LR' else ''),18)
 for f,label in [(.25,r['parent_cardinality']),(.75,r['child_cardinality'])]:
  tx,ty=pathmid(pts,f);c.text(tx+10,ty-29,label.replace('0..N','0..n'),24,bg=True)
 c.edges.append({'id':r['id'],'parent':r['parent'],'child':r['child'],'column':r['column'],'kind':r['kind'],'route':pts,'diamond':[dx,dy],'cardinality':r['cardinality']})
def header(c,title,sub='下划线为主键；实线为物理外键联系，虚线为逻辑联系；0..n表示可无或多条'):
 c.text(c.w/2,58,title,44,True);c.text(c.w/2,118,sub,25)
def footer(c):
 c.text(c.w/2,c.h-83,'矩形＝实体　菱形＝联系　椭圆＝主要属性；属性从当前DDL选取，外键字段以联系表达。',25)
 c.text(c.w/2,c.h-38,'基数为实际可选性：父端1或0..1，子端0..n；SQL结构核对，未连接实际数据库。',24)
# Full relationship coverage in traditional Chen diagrams, domains may repeat user as shared entity.
core={'user':(500,650),'goal':(1750,650),'plan':(3000,650),'partner':(500,2100),'checkin':(1750,2100),'plan_task':(3000,2100)}
route={
'plan.user_id':([(500,240),(3000,240)],.5),
'checkin.user_id':([(1000,950),(1000,1550),(2100,1550),(2100,1950)],.48),
'checkin.partner_id':([(100,650),(100,2580),(1750,2580)],.64),
'partner.partner_id':([(240,650),(240,2100)],.5),
'partner.goal_id':([(1500,700),(1400,1000),(1400,1200),(760,1200),(760,2100)],.39)}
c=C(3500,2800);header(c,'系统核心业务 E-R 图（实体—联系—属性）')
core_rs=[r for r in rels if r['child'] in core and r['parent'] in core]
for r in core_rs:bs,f=route.get(r['child']+'.'+r['column'],(None,.5));relation(c,r,core,bs,f)
for n,p in core.items():entity(c,n,p)
footer(c);c.save('chen-core')
# Social domain: use two pages to keep attributes and role links clear.
social={'user':(500,650),'circle':(1750,650),'circle_member':(3000,650),'post':(1750,2050),'comment':(500,2050),'like_record':(3000,2050)}
sroutes={'circle_member.user_id':([(500,240),(3000,240)],.5),'post.user_id':([(1030,650),(1030,1370),(1750,1370)],.5),'like_record.user_id':([(130,650),(130,2560),(3000,2560)],.7)}
c=C(3500,2780);header(c,'圈子与动态互动 E-R 图')
for r in rels:
 if r['child'] in social and r['parent'] in social:bs,f=sroutes.get(r['child']+'.'+r['column'],(None,.5));relation(c,r,social,bs,f)
for n,p in social.items():entity(c,n,p)
footer(c);c.save('chen-social')
topic={'user':(550,650),'circle':(2200,650),'topic_reply':(550,1900),'topic':(2200,1900)}
c=C(2800,2500);header(c,'圈子话题与回复 E-R 图')
for r in rels:
 if r['child'] in topic and r['parent'] in topic:
  if r['child']=='circle':continue # already covered on social page
  bs=[(1350,650),(1350,1320),(2200,1320)] if r['child']=='topic' and r['parent']=='user' else None
  relation(c,r,topic,bs,.5)
for n,p in topic.items():entity(c,n,p)
footer(c);c.save('chen-topic')
# Support star split to prevent multiple user role lines crossing attribute groups.
support={'user':(1750,1450),'achievement':(500,650),'ai_chat_history':(1750,550),'file_upload':(3000,650),'vip_membership':(650,2350),'vip_order':(2850,2350)}
c=C(3500,2900);c.support_mode=True;header(c,'用户成就、AI、文件与会员 E-R 图')
for r in rels:
 if r['child'] in support and r['parent'] in support:relation(c,r,support)
for n,p in support.items():entity(c,n,p)
footer(c);c.save('chen-support')
msgs={'user':(1650,650),'message':(650,1900),'notification':(2650,1900)}
mr={'message.from_user_id':([(650,650)],.5),'message.to_user_id':([(1400,700),(1020,1000),(1020,1900)],.5),'notification.user_id':([(2650,650)],.5),'notification.from_user_id':([(1900,700),(2280,1000),(2280,1900)],.5)}
c=C(3300,2600);header(c,'私信、通知与方案模板 E-R 图')
for r in rels:
 if r['child'] in msgs and r['parent'] in msgs:bs,f=mr[r['child']+'.'+r['column']];relation(c,r,msgs,bs,f)
for n,p in msgs.items():entity(c,n,p)
entity(c,'plan_template',(1650,2100));c.text(1650,2450,'模板独立存储；未持久关联到计划表',23)
footer(c);c.save('chen-message')
# Single overall diagram: all 21 entities / all 33 fixed FK/LR relationships, no attributes to avoid a giant unreadable poster.
# Use stacked domain panels, with a common user in each; explicit shared-entity notation prevents inventing users.
# Deliver an atlas as total image: every panel remains a true rectangle/diamond/oval diagram, never table cards.
imgs=[Image.open(OUT/(n+'.png')) for n in ['chen-core','chen-social','chen-topic','chen-support','chen-message']]
width=7000;rowh=[max(imgs[0].height,imgs[1].height),max(imgs[2].height,imgs[3].height),imgs[4].height];height=sum(rowh)+310
atlas=Image.new('RGB',(width,height),'white');d=ImageDraw.Draw(atlas);f=ImageFont.truetype(BOLD,58);title='数据库 E-R 总图（传统实体—联系表示法）';d.text(((width-d.textlength(title,font=f))/2,25),title,font=f,fill='black');f=ImageFont.truetype(FONT,32);sub='21个实体 · 29个物理外键联系 · 4个逻辑联系；各分区中的同名实体表示同一实体';d.text(((width-d.textlength(sub,font=f))/2,115),sub,font=f,fill='black')
spots=[(0,210),(3500,210),(0,210+rowh[0]),(3500,210+rowh[0]),(1850,210+rowh[0]+rowh[1])]
svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}"><rect width="100%" height="100%" fill="white"/>',f'<text x="3500" y="95" text-anchor="middle" font-family="Microsoft YaHei" font-size="58">{title}</text>',f'<text x="3500" y="165" text-anchor="middle" font-family="Microsoft YaHei" font-size="32">{sub}</text>']
covered=set();ents=set()
for im,(x,y),n in zip(imgs,spots,['chen-core','chen-social','chen-topic','chen-support','chen-message']):
 atlas.paste(im,(x,y));root=ET.parse(OUT/(n+'.svg')).getroot();svg.append(f'<g transform="translate({x},{y})">');svg.extend(ET.tostring(el,encoding='unicode') for el in root);svg.append('</g>');layout=json.loads((OUT/(n+'.layout.json')).read_text(encoding='utf-8'));covered|={r['id'] for r in layout['relationships']};ents|={e['table'] for e in layout['entities']}
assert covered=={r['id'] for r in rels};assert ents==set(tables)
atlas.save(OUT/'chen-er-overview.png',dpi=(240,240));(OUT/'chen-er-overview.svg').write_text('\n'.join(svg+['</svg>']),encoding='utf-8')
md='''# 传统实体—联系 E-R 图

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
'''
(OUT/'README.md').write_text(md,encoding='utf-8')
for n,attrs in A.items():
 for col,label in attrs:assert any(c['name']==col for c in tables[n]['columns'])
(OUT/'validation-report.json').write_text(json.dumps({'entities':len(ents),'fixed_relationships':len(covered),'FK':29,'LR':4,'sql_hashes_verified':True,'runtime_database_verified':False},ensure_ascii=False,indent=2),encoding='utf-8')
print('Generated Chen ER: 21 entities / 33 relationships / 5 domain diagrams + overview')
