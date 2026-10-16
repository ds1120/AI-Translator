#!/usr/bin/env python3
"""Send a signed webhook without LINE credentials. Default key is mock-only."""
import argparse, base64, hashlib, hmac, json, time, urllib.request, uuid
p=argparse.ArgumentParser()
p.add_argument('--url',default='http://127.0.0.1:8080/webhooks/line')
p.add_argument('--text',default='내일 아침 9시에 출발할게.')
p.add_argument('--repeat',type=int,default=2)
a=p.parse_args()
body=json.dumps({'events':[{'type':'message','mode':'active','webhookEventId':str(uuid.uuid4()),'timestamp':int(time.time()*1000),
 'replyToken':'mock-reply-token','source':{'type':'group','groupId':'mock-group','userId':'mock-user'},
 'message':{'type':'text','id':'mock-message','text':a.text}}]},ensure_ascii=False,separators=(',',':')).encode()
signature=base64.b64encode(hmac.new(b'mock-channel-secret',body,hashlib.sha256).digest()).decode()
for _ in range(a.repeat):
 req=urllib.request.Request(a.url,data=body,headers={'Content-Type':'application/json','X-Line-Signature':signature})
 with urllib.request.urlopen(req,timeout=5) as res: print('Webhook HTTP',res.status)
print('Repeated the SAME event ID. Expect one outcome=SENT log; no message is sent to real LINE in mock mode.')
