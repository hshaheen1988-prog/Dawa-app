import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__))); os.makedirs('shots', exist_ok=True)
import subprocess, time, json
from playwright.sync_api import sync_playwright
srv = subprocess.Popen(["python3","-m","http.server","8783","-d", ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(1); errs=[]
stub = open('stub.js').read() if False else """
window.__calls=[]; window.__pending='[]';
window.DawaNative={isNative(){return true},schedule(j){__calls.push(['schedule',JSON.parse(j)])},setWidget(j){__calls.push(['widget',JSON.parse(j)])},
takePendingMarks(){const p=__pending;__pending='[]';return p},notifyNow(){},testAlarm(){},testVoice(t,l){__calls.push(['voice',t,l])},
status(){return JSON.stringify({notif:true,exact:true,fullScreen:true,battery:true,count:3,next:Date.now()+3600000,sdk:34,maker:'samsung'})},
notificationsAllowed(){return true},exactAlarmsAllowed(){return true},openNotificationSettings(){},openExactAlarmSettings(){},openFullScreenSettings(){},openBatterySettings(){},openAppSettings(){},share(){}};"""
try:
  with sync_playwright() as p:
    b = p.chromium.launch(); ctx = b.new_context(viewport={"width":390,"height":844}, locale="ar", device_scale_factor=2)
    ctx.add_init_script(stub)
    pg = ctx.new_page()
    pg.on("pageerror", lambda e: errs.append("PAGEERR "+str(e))); pg.on("console", lambda m: m.type=="error" and errs.append(m.text))
    pg.goto("http://localhost:8783/")
    pg.fill("#obName","حمزة"); pg.fill("#obPhone","0791234567"); pg.click("#onboardForm button[type=submit]")
    def addmed(name, times, amt=None, pattern=None):
      pg.click(".fab"); pg.fill("#medForm [name=name]", name)
      if pattern:
        pg.click("#medForm .seg button[data-mode=pattern]"); pg.fill(".p-amt", str(pattern[0]))
        for v in pattern[1:]: pg.locator("#patternList + .add-link").click(); pg.locator(".p-amt").last.fill(str(v))
      pg.locator("#timesList .t-in").first.fill(times[0])
      for tm in times[1:]: pg.locator("#timesList + .add-link").click(); pg.locator("#timesList .t-in").last.fill(tm)
      if amt: pg.locator(".s-amt").first.fill(str(amt))
      pg.click("#medForm .opt[data-meal=after]"); pg.click("#medForm button[type=submit]"); pg.wait_for_timeout(250)
    addmed("وارفارين", ["20:00"], pattern=[2,2,2.5])
    addmed("أسيتازولاميد", ["08:00","20:00"], amt=1)
    # INR: add 3 results incl. past via "save & add another"
    pg.locator(".inr-card").click(); pg.wait_for_timeout(250)
    pg.click("#sheet >> text=إضافة نتيجة فحص"); pg.wait_for_timeout(250)
    pg.fill("#inrForm [name=value]","2.4"); pg.fill("#inrForm [name=dose]","5 ملغ يومياً")
    pg.click("#inrForm .opt[data-days='14']")
    pg.click("#inrMore"); pg.wait_for_timeout(250)
    print("after more: date =", pg.input_value("#inrForm [name=date]"), "| value empty:", pg.input_value("#inrForm [name=value]")=="")
    pg.fill("#inrForm [name=value]","3.6"); pg.click("#inrMore"); pg.wait_for_timeout(200)
    pg.fill("#inrForm [name=value]","1.7"); pg.click("#inrChg"); pg.wait_for_timeout(100)
    pg.screenshot(path="shots/f_inr_form.png", full_page=True)
    pg.click("#inrForm button[type=submit]"); pg.wait_for_timeout(300)
    st = pg.evaluate("S.inr[S.active]")
    print("INR tests:", [(x['date'], x['value']) for x in sorted(st['tests'], key=lambda x:x['date'])], "next:", st['next'])
    pg.screenshot(path="shots/f_inr_sheet.png", full_page=True)
    pg.evaluate("closeModal()"); pg.wait_for_timeout(200)
    pg.screenshot(path="shots/f_today.png", full_page=True)
    # speech + widget
    calls = pg.evaluate("__calls"); sched=[c for c in calls if c[0]=='schedule'][-1][1]; wid=[c for c in calls if c[0]=='widget'][-1][1]
    print("speech:", [a.get('speech') for a in sched[:2]])
    print("inr reminder:", [ (a['title'], time.strftime('%d %H:%M', time.localtime(a['at']/1000))) for a in sched if 'INR' in a['title']])
    print("widget labels:", json.dumps(wid['labels'], ensure_ascii=False), "| note:", repr(wid.get('note')))
    print("note if tomorrow:", pg.evaluate("(()=>{const r=inrOf(S.active), o=r.next; r.next=addDays(today(),1); const a=widgetData().note; r.next=today(); const b=widgetData().note; r.next=o; return [a,b]})()"))
    print("SNAPSIZE", pg.evaluate("(()=>{const sh=window.famShared; window.famShared=()=>true; const s=famSnapshot(); const n=JSON.stringify(s).length; return [n, s.slots.length, s.meds.length, new Blob([JSON.stringify(s)]).size]})()"))
    print("widget days:", wid['days'], "| first items:", [(i['time'], i['text'].replace('\n',' / ')) for i in wid['items'][:3]])
    print("amountSpeech:", pg.evaluate("[1,2,0.5,1.5,2.5,3,0.25,1.75].map(a=>amountSpeech(a,'حبة'))"), pg.evaluate("amountSpeech(5,'مل')"), pg.evaluate("amountSpeech(2.5,'مل')"))
    # day popup from week strip
    pg.locator(".wday").nth(4).click(); pg.wait_for_timeout(250); pg.screenshot(path="shots/f_day.png", full_page=True)
    pg.evaluate("closeModal()")
    pg.click(".week-head button"); pg.wait_for_timeout(250); pg.screenshot(path="shots/f_month.png", full_page=True)
    pg.locator(".cal.month .c").nth(27).click(); pg.wait_for_timeout(250); pg.screenshot(path="shots/f_month_day.png", full_page=True)
    pg.evaluate("closeModal()")
    # Ramadan
    pg.click("#setBtn"); pg.wait_for_timeout(200); pg.screenshot(path="shots/f_settings.png", full_page=True)
    pg.click("#sheet >> text=اسمع"); pg.wait_for_timeout(100)
    print("voice test:", [c for c in pg.evaluate("__calls") if c[0]=='voice'][-1][1])
    pg.click("#sheet .set-row:has-text('وضع رمضان')"); pg.wait_for_timeout(250)
    pg.click("#sheet .tgl"); pg.wait_for_timeout(250)
    pg.screenshot(path="shots/f_ramadan.png", full_page=True)
    print("ramadan:", pg.evaluate("S.ramadan"), "| hijri month today:", pg.evaluate("hijriMonth(today())"))
    print("today slots (ramadan):", pg.evaluate("slotsFor(S.active, today()).map(s=>s.med.name+'@'+s.time)"))
    pg.evaluate("closeModal()"); pg.wait_for_timeout(200); pg.screenshot(path="shots/f_today_ramadan.png", full_page=True)
    print("hijri for 2027-02-20:", pg.evaluate("hijriMonth('2027-02-20')"), "end from 2027-02-09:", pg.evaluate("ramadanEnd('2027-02-09')"))
    b.close()
finally: srv.terminate()
print("ERRORS:", errs)
