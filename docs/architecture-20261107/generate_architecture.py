from pathlib import Path
from html import escape
import math
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
W,H=2200,1520
im=Image.new('RGB',(W,H),'white'); d=ImageDraw.Draw(im)
FONT='C:/Windows/Fonts/msyh.ttc'; BOLD='C:/Windows/Fonts/msyhbd.ttc'
svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="100%" height="100%" fill="white"/>']
ink='#172b42'; blue='#2e668e'; muted='#536477'; orange='#a76522'
def text(x,y,s,size=25,color=ink,bold=False,anchor='middle'):
 f=ImageFont.truetype(BOLD if bold else FONT,size)
 width=d.textlength(s,font=f); xx=x-width/2 if anchor=='middle' else (x-width if anchor=='end' else x)
 d.text((xx,y),s,font=f,fill=color)
 svg.append(f'<text x="{x}" y="{y+size}" text-anchor="{anchor}" font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{color}">{escape(s)}</text>')
def box(x,y,w,h,fill='#f3f7fb',stroke='#97aec3',radius=15):
 d.rounded_rectangle((x,y,x+w,y+h),radius=radius,fill=fill,outline=stroke,width=2)
 svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
def arrow(points,label=None,lx=0,ly=0,dashed=False,color=blue):
 if dashed:
  for a,b in zip(points,points[1:]):
   length=math.dist(a,b)
   for t in range(0,int(length),17):
    q=min(t+10,length); p1=tuple(a[i]+(b[i]-a[i])*t/length for i in (0,1));p2=tuple(a[i]+(b[i]-a[i])*q/length for i in (0,1))
    d.line([p1,p2],fill=color,width=3)
 else:d.line(points,fill=color,width=3)
 a,b=points[-2:]; ang=math.atan2(b[1]-a[1],b[0]-a[0]);tip=[b,(b[0]-15*math.cos(ang-.42),b[1]-15*math.sin(ang-.42)),(b[0]-15*math.cos(ang+.42),b[1]-15*math.sin(ang+.42))]
 d.polygon(tip,fill=color)
 pts=' '.join(f'{x},{y}' for x,y in points);dash=' stroke-dasharray="10 7"' if dashed else ''
 svg.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="3"{dash}/><polygon points="'+ ' '.join(f'{x},{y}' for x,y in tip)+f'" fill="{color}"/>')
 if label:text(lx,ly,label,22,color)

text(1100,30,'系统总体架构与实际调用方向',42,bold=True)
text(1100,92,'配置与源码核对｜2026-11-07｜本地开发配置；外部依赖未做连通验收',25,muted)
box(480,175,960,955,'#fafcfe','#8da5bd',22)
text(960,195,'Spring Boot 3.2.5 后端单体应用 · Java 17 · :8081',29,bold=True)
text(960,241,'逻辑分层，不代表独立部署的微服务',22,muted)

box(65,300,305,170,'#edf5fd')
text(217,322,'微信原生小程序',30,bold=True)
text(217,375,'JS / WXML / WXSS',23)
text(217,414,'development 配置',23)
box(65,590,305,135,'#edf5fd')
text(217,610,'管理端浏览器',28,bold=True)
text(217,660,'同一后端提供 /admin',23)

box(550,300,820,150)
text(960,316,'Controller 接口层',32,bold=True)
text(960,370,'Spring MVC · /api/** · JSON / multipart',24)
text(960,410,'登录 / 管理员拦截器：JWT 校验（公开接口除外）',23)
arrow([(370,345),(550,345)])
text(460,278,'HTTP 请求',22)
text(285,495,'http://localhost:8081',22,muted)
arrow([(370,650),(410,650),(410,415),(550,415)])

box(550,530,820,350,'#edf6f3','#89b3a4')
text(960,546,'Service 业务层与外部服务适配',31,bold=True)
text(960,608,'User / Goal / Plan / Checkin / Partner 等业务服务',23)
text(960,655,'UserServiceImpl → RedisUtil → StringRedisTemplate',23)
text(960,702,'AiServiceImpl：对话、建议、计划生成',23)
text(960,749,'OssServiceImpl：上传；OcrServiceImpl：URL 识别',23)
text(960,806,'课表：Controller 先调 OSS，取得 URL 后再调 OCR',22,muted)
arrow([(960,450),(960,530)],'调用',1005,475)
box(550,970,820,130)
text(960,987,'Mapper 数据访问层',31,bold=True)
text(960,1044,'MyBatis Mapper 接口 / XML · SQL 执行与结果映射',24)
arrow([(960,880),(960,970)],'调用 / 数据读写',1085,910)
arrow([(550,390),(505,390),(505,1030),(550,1030)],dashed=True,color=orange)
text(450,900,'部分 Controller',21,orange,anchor='end')
text(450,935,'直接调用 Mapper',21,orange,anchor='end')

box(550,1205,355,145,'#fff8ec','#c9ae77')
text(727,1220,'Redis',31,bold=True)
text(727,1272,'127.0.0.1:6379',25)
text(727,1310,'验证码 · TTL 300 秒',23)
arrow([(550,690),(530,690),(530,1170),(727,1170),(727,1205)])
text(630,1137,'GET / SETEX / DEL',22)
box(1010,1205,360,145,'#fff8ec','#c9ae77')
text(1190,1220,'MySQL',31,bold=True)
text(1190,1272,'localhost:3306 / zilvzi',24)
text(1190,1310,'业务数据、AI 历史、计划模板',21)
arrow([(1190,1100),(1190,1205)],'JDBC / Druid',1300,1147)

text(1895,166,'外部平台与云服务',30,bold=True)
for y,title,l1,l2 in [
 (235,'微信开放接口','api.weixin.qq.com','jscode2session · 登录换取 openid'),
 (455,'DeepSeek AI','api.deepseek.com/chat/completions','模型：deepseek-chat'),
 (695,'阿里云 OSS','oss-cn-hangzhou.aliyuncs.com','后端 SDK 上传 · 返回对象访问 URL'),
 (935,'阿里云 OCR','ocr-api.cn-hangzhou.aliyuncs.com','RecognizeAdvanced · 传入图片 URL')]:
 box(1710,y,430,150,'#f5f1fb','#b3a2c8')
 text(1925,y+14,title,29,bold=True)
 text(1925,y+68,l1,20)
 text(1925,y+108,l2,20)
arrow([(1370,585),(1490,585),(1490,310),(1710,310)])
text(1585,240,'UserService',22)
text(1585,274,'HTTPS GET',21)
arrow([(1370,710),(1550,710),(1550,530),(1710,530)])
text(1585,456,'AiService',22)
text(1585,490,'HTTPS POST',21)
arrow([(1370,755),(1620,755),(1620,770),(1710,770)])
text(1545,723,'OSS SDK 上传',21)
arrow([(1370,815),(1540,815),(1540,1010),(1710,1010)])
text(1590,938,'OCR SDK',22)
text(1590,973,'图片 URL',21)
arrow([(2140,1000),(2180,1000),(2180,770),(2140,770)],dashed=True,color=orange)
text(1910,1110,'OCR 按 URL 读取对象（须可访问）',23,orange)

text(1100,1395,'图例：实线 → 发起调用 / 数据访问；虚线 → 跨层调用或按 URL 获取资源。返回数据沿调用链回传，省略反向箭头。',23,muted)
text(1100,1435,'说明：未绘制虚构的网关、消息队列或微服务；Redis 不是计划模板缓存；图中不含账号、口令及密钥。',23,muted)
svg.append('</svg>')
(OUT/'system-architecture.svg').write_text('\n'.join(svg),encoding='utf-8')
im.save(OUT/'system-architecture.png',dpi=(240,240))
print('Generated architecture SVG and PNG:',W,H)
