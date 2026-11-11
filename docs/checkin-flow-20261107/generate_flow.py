from pathlib import Path
from html import escape
import math
from PIL import Image,ImageDraw,ImageFont
O=Path(__file__).resolve().parent
W,H=2280,2740
im=Image.new('RGB',(W,H),'white');d=ImageDraw.Draw(im)
F='C:/Windows/Fonts/msyh.ttc';B='C:/Windows/Fonts/msyhbd.ttc'
sv=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="100%" height="100%" fill="white"/>']
ink='#19334a';blue='#326b92';green='#27694e';red='#a74442';amber='#97631f'
def text(x,y,s,size=25,c=ink,bold=False):
 f=ImageFont.truetype(B if bold else F,size);tw=d.textlength(s,font=f);d.text((x-tw/2,y),s,font=f,fill=c)
 sv.append(f'<text x="{x}" y="{y+size}" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{c}">{escape(s)}</text>')
def rect(cx,y,w,h,lines,fill='#f0f5fa',stroke='#9ab0c4',title=None):
 x=cx-w/2;d.rounded_rectangle((x,y,x+w,y+h),radius=16,fill=fill,outline=stroke,width=2);sv.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="16" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
 if title:text(cx,y+15,title,29,stroke,True)
 start=y+(62 if title else (h-len(lines)*38)/2)
 for i,l in enumerate(lines):text(cx,start+i*38,l,25)
def diamond(cx,y,w,h,lines):
 pts=[(cx,y),(cx+w/2,y+h/2),(cx,y+h),(cx-w/2,y+h/2)];d.polygon(pts,fill='#fff8e9',outline='#b79551',width=2);sv.append('<polygon points="'+' '.join(f'{x},{yy}' for x,yy in pts)+'" fill="#fff8e9" stroke="#b79551" stroke-width="2"/>')
 for i,l in enumerate(lines):text(cx,y+(h-len(lines)*37)/2+i*37,l,25)
def arrow(pts,label=None,lx=0,ly=0,c=blue,dash=False):
 for a,b in zip(pts,pts[1:]):
  if dash:
   dist=math.dist(a,b)
   for t in range(0,int(dist),18):
    q=min(t+10,dist);d.line([tuple(a[i]+(b[i]-a[i])*v/dist for i in (0,1)) for v in (t,q)],fill=c,width=3)
  else:d.line([a,b],fill=c,width=3)
 a,b=pts[-2:];ang=math.atan2(b[1]-a[1],b[0]-a[0]);head=[b,(b[0]-15*math.cos(ang-.4),b[1]-15*math.sin(ang-.4)),(b[0]-15*math.cos(ang+.4),b[1]-15*math.sin(ang+.4))];d.polygon(head,fill=c)
 sv.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in pts)+f'" fill="none" stroke="{c}" stroke-width="3"'+(' stroke-dasharray="10 8"' if dash else '')+'/>'+'<polygon points="'+' '.join(f'{x},{y}' for x,y in head)+f'" fill="{c}"/>')
 if label:text(lx,ly,label,23,c)
text(1140,25,'打卡提交与搭子审核业务流程',43,bold=True)
text(1140,88,'当前源码实现 · 2026-11-07 · 普通打卡与审核通过共享 approved 状态',26)
rect(1140,155,480,70,['用户登录后选择目标 / 任务'])
arrow([(1140,225),(1140,260)])
rect(1140,260,800,120,['POST /api/checkin/do','提交 taskId（优先）或 goalId；可携带凭证'])
arrow([(1140,380),(1140,420)])
diamond(1140,420,830,180,['参数及当前入口校验通过？','任务：归属 / 完成状态 / 日期','目标：当日是否已有记录'])
rect(340,440,470,125,['返回错误，结束本次请求','不创建新打卡记录'],fill='#fff0ee',stroke=red)
arrow([(725,510),(575,510)],'否',645,473,c=red)
arrow([(1140,600),(1140,650)],'是',1190,610)
rect(1140,650,900,125,['仅任务入口：先置 completed = 1（断卡重置图标）','尝试刷新计划完成天数；目标入口跳过此步'])
arrow([(1140,775),(1140,810)])
diamond(1140,810,800,160,['任务入口已存在同任务同日期记录？','目标入口已有记录已在前面拒绝'])
rect(340,825,470,135,['直接返回原记录，结束','不新建，不重复触发奖励'],fill='#eef1f6')
arrow([(740,890),(575,890)],'是',650,852)
arrow([(1140,970),(1140,1010)],'否 / 目标入口',1260,977)
diamond(1140,1010,830,175,['存在已接受且目标类别匹配的搭子？','由后端重新判定，类别做标准化'])
arrow([(725,1097),(510,1097),(510,1240)],'否',630,1058,c=green)
arrow([(1555,1097),(1730,1097),(1730,1240)],'是',1630,1058,c=amber)
rect(510,1240,780,150,['新建记录：status = approved','普通打卡直接生效，无需搭子审核'],fill='#eef8f2',stroke=green,title='① 普通打卡')
arrow([(510,1390),(510,1440)],c=green)
rect(510,1440,780,155,['按 approved 重算目标连续 / 累计天数','调用成就检查；用户存在时增加 10 经验','满足条件时解锁勋章'],fill='#eef8f2',stroke=green)
arrow([(510,1595),(510,1645)],c=green)
rect(510,1645,650,75,['返回 approved；本次普通打卡结束'],fill='#eef8f2',stroke=green)
rect(1730,1240,850,150,['新建记录：status = pending；保存凭证字段','通知匹配搭子，并写入打卡凭证私信'],fill='#fff8e9',stroke=amber,title='② 待审核打卡')
arrow([(1730,1390),(1730,1435)],c=amber)
rect(1730,1435,850,120,['返回 pending，提交请求结束；等待另一请求','此时不更新目标打卡统计、不加经验'],fill='#fff8e9',stroke=amber)
arrow([(1730,1555),(1730,1600)],'异步人工审核',1880,1567,c=amber,dash=True)
rect(1730,1600,850,120,['搭子查看待审列表或凭证私信后提交结果','POST /api/checkin/review · approved / comment'])
arrow([(1730,1720),(1730,1760)])
diamond(1730,1760,870,170,['记录存在、仍 pending，且审核校验通过？','权限检查存在局限，详见核对说明'])
rect(610,1780,760,120,['返回错误，当前审核请求不改变记录状态','例如：记录不存在、已审核、未通过权限检查'],fill='#fff0ee',stroke=red)
arrow([(1295,1845),(990,1845)],'否',1150,1807,c=red)
arrow([(1730,1930),(1730,1970)],'是',1780,1938)
diamond(1730,1970,600,120,['approved 为 true？'])
arrow([(1430,2030),(1070,2030),(1070,2150)],'是：通过',1245,1990,c=green)
arrow([(2030,2030),(2030,2150)],'否：驳回',2130,2060,c=red)
rect(1070,2150,800,205,['status = approved；记录审核人 / 时间 / 评语','尝试更新凭证私信；重算目标打卡统计','检查成就、增加 10 经验并通知提交者','返回 approved；审核请求结束'],fill='#eef8f2',stroke=green,title='③ 审核通过')
rect(1960,2150,570,205,['status = rejected；记录审核信息','尝试更新凭证私信并通知提交者','不加经验，不计 approved 天数','返回 rejected；审核请求结束'],fill='#fff0ee',stroke=red,title='④ 审核驳回')
rect(1140,2410,2110,220,[],fill='#f7f8fa',stroke='#708396')
text(1140,2425,'实现边界（是现有行为，不能按理想流程补画）',28,bold=True)
for i,s in enumerate([
 '待审核及驳回时，关联任务仍可能 completed = 1；驳回不会自动回滚任务，也没有完整的修改重提闭环。',
 '普通打卡与审核通过都写 approved；“普通”不是第四种数据库状态。日历日期查询未过滤审核状态。',
 '审核 SQL 未以 pending 为更新条件，也未做原子抢占；不能据此宣称并发审核“先到先得”已保证。',
 '仅为当前源码流程核对；未进行真实接口、数据库或微信端运行验收。实线为本次请求，虚线为跨请求等待。']):text(1140,2470+i*36,s,23)
sv.append('</svg>')
(O/'checkin-business-flow.svg').write_text('\n'.join(sv),encoding='utf-8')
im.save(O/'checkin-business-flow.png',dpi=(240,240))
print('Generated flow SVG / PNG:',W,H)
