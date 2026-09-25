import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__))); os.makedirs('shots', exist_ok=True)
import subprocess, time, json
from playwright.sync_api import sync_playwright
web = subprocess.Popen(["python3","-m","http.server","8792","-d", ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
api = subprocess.Popen(["python3","mock_supa.py"], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
time.sleep(1.2); errs=[]
init = "window.DAWA_SUPA_URL='http://127.0.0.1:8791';"
stub = init + """window.__calls=[];window.__pending='[]';window.DawaNative={isNative(){return true},schedule(j){__calls.push(['schedule',JSON.parse(j)])},setWidget(){},setFamily(j){__calls.push(['family',JSON.parse(j)])},
takePendingMarks(){const p=__pending;__pending='[]';return p},notifyNow(){},testAlarm(){},testVoice(){},status(){return JSON.stringify({notif:true,exact:true,fullScreen:true,battery:true,count:1,next:0,sdk:34,maker:'samsung'})},
notificationsAllowed(){return true},exactAlarmsAllowed(){return true},openNotificationSettings(){},openExactAlarmSettings(){},openFullScreenSettings(){},openBatterySettings(){},openAppSettings(){},share(t,c){__calls.push(['share',c])}};"""
def hm(delta):
    t = time.localtime(time.time()+delta*60); return f"{t.tm_hour:02d}:{t.tm_min:02d}"
try:
  with sync_playwright() as p:
    b = p.chromium.launch()
    # ---------- patient phone ----------
    pc = b.new_context(viewport={"width":390,"height":844}, locale="ar", device_scale_factor=2); pc.add_init_script(init)
    pp = pc.new_page(); pp.on("pageerror", lambda e: errs.append("P "+str(e))); pp.on("console", lambda m: m.type=="error" and errs.append("P "+m.text))
    pp.on("dialog", lambda d: d.accept())
    pp.goto("http://localhost:8792/"); pp.fill("#obName","أبو حمزة"); pp.fill("#obPhone","0790000001"); pp.click("#onboardForm button[type=submit]")
    for name, tm in [("وارفارين", hm(-60)), ("فيتامين د", hm(-50)), ("أسيتازولاميد", hm(120))]:
        pp.click(".fab"); pp.fill("#medForm [name=name]", name); pp.locator("#timesList .t-in").first.fill(tm); pp.click("#medForm button[type=submit]"); pp.wait_for_timeout(250)
    pp.click("nav.tabs .tab[data-tab=family]"); pp.wait_for_timeout(200)
    pp.click(".fam-hub .btn >> nth=0"); pp.wait_for_timeout(300)
    pp.click("#sheet >> text=تفعيل المشاركة"); pp.wait_for_timeout(1500)
    code = pp.evaluate("S.family.code"); print("patient code:", code)
    # un-share vitamin D
    pp.locator("#sheet .set-row:has-text('فيتامين د') .tgl").click(); pp.wait_for_timeout(600)
    pp.screenshot(path="shots/fam_patient_share.png", full_page=True)
    pp.evaluate("closeModal()")
    # patient takes warfarin
    pp.click("nav.tabs .tab[data-tab=today]"); pp.wait_for_timeout(200)
    pp.locator(".dose:has-text('وارفارين') .b-take").click(); pp.wait_for_timeout(3500)  # debounce push
    # ---------- follower phone (native stub) ----------
    fc = b.new_context(viewport={"width":390,"height":844}, locale="ar", device_scale_factor=2); fc.add_init_script(stub)
    fp = fc.new_page(); fp.on("pageerror", lambda e: errs.append("F "+str(e))); fp.on("console", lambda m: m.type=="error" and errs.append("F "+m.text))
    fp.on("dialog", lambda d: d.accept())
    fp.goto("http://localhost:8792/"); fp.fill("#obName","حمزة"); fp.fill("#obPhone","0790000002"); fp.click("#onboardForm button[type=submit]")
    fp.click("nav.tabs .tab[data-tab=family]"); fp.wait_for_timeout(200)
    fp.click(".fam-hub .btn >> nth=1"); fp.wait_for_timeout(300)
    fp.fill("#famCode", code.lower()); fp.click("#sheet .btn"); fp.wait_for_timeout(2000)
    fp.screenshot(path="shots/fam_follower_tab.png", full_page=True)
    f = fp.evaluate("S.family.following[0]")
    print("follower sees:", f['name'], "| meds:", [m['name'] for m in f['snapshot']['meds']], "| marks:", {k.split('|')[2]: v['s'] for k, v in f['marks'].items()})
    fp.click(".fam-person"); fp.wait_for_timeout(300)
    fp.screenshot(path="shots/fam_follower_person.png", full_page=True)
    # enable remind on acetazolamide
    fp.locator("#sheet .set-row:has-text('أسيتازولاميد') .opt >> nth=0").click(); fp.wait_for_timeout(600)
    sched = [c for c in fp.evaluate("__calls") if c[0]=='schedule'][-1][1]
    fam_items = [a for a in sched if a['keys'].startswith('F|') or a.get('kind')=='check']
    print("follower alarms:", [(a.get('kind','remind'), a['title'], time.strftime('%H:%M', time.localtime(a['at']/1000))) for a in fam_items][:4])
    print("setFamily cfg has id/secret:", all(k in [c for c in fp.evaluate("__calls") if c[0]=='family'][-1][1] for k in ('id','secret','url')))
    # follower gives the late... none late except warfarin taken; give acetazolamide (pending)
    fp.locator("#sheet .set-row:has-text('أسيتازولاميد') .b-soft").click(); fp.wait_for_timeout(1500)
    # ---------- back on patient phone: pull ----------
    pp.evaluate("famSync(true)"); pp.wait_for_timeout(2500)
    st = pp.evaluate("Object.fromEntries(Object.entries(S.log).map(([k,v])=>[k.split('|')[0]===S.meds.find(m=>m.name==='أسيتازولاميد').id?'aceta':'warf', v.status + (v.by?(' by '+v.by):'')]))")
    print("patient log after follower gave dose:", st)
    pp.click("nav.tabs .tab[data-tab=today]"); pp.wait_for_timeout(300); pp.screenshot(path="shots/fam_patient_today.png", full_page=True)
    # server-side check of status for the missed-dose alarm (vitamin D is not shared → not visible)
    snap_slots = fp.evaluate("S.family.following[0].snapshot.slots.map(s=>s.k)")
    print("vitamin D shared?", any(pp.evaluate(f"S.meds.find(m=>m.name==='فيتامين د').id") in k for k in snap_slots))
    # ---------- traffic: repeated syncs with nothing new ----------
    spy = """(()=>{ window.__net=[]; const of=window.fetch; window.fetch=async (u,o)=>{ const r=await of(u,o); const c=r.clone(); const t=await c.text(); __net.push([String(u).split('/').pop(), (o&&o.body||'').length, t.length]); return r; }; })()"""
    pp.evaluate(spy); fp.evaluate(spy)
    for i in range(4):
        pp.evaluate("famSync(true)"); fp.evaluate("famSync(true)"); pp.wait_for_timeout(700)
    pn = pp.evaluate("__net"); fn = fp.evaluate("__net")
    print("patient calls:", [(n, up) for n, up, down in pn])
    print("follower downloads (bytes):", [down for n, down_, down in [(x[0], x[1], x[2]) for x in fn]])
    # patient changes something → exactly one push, follower gets the new schedule once
    pp.evaluate("__net=[]; S.meds.find(m=>m.name==='وارفارين').notes='بعد العشا'; save()"); pp.wait_for_timeout(3500)
    print("push after change:", [n for n, _, _ in pp.evaluate("__net")])
    fp.evaluate("__net=[]"); fp.evaluate("famSync(true)"); fp.wait_for_timeout(800); fp.evaluate("famSync(true)"); fp.wait_for_timeout(800)
    print("follower after change (bytes):", [d for _, _, d in fp.evaluate("__net")], "| notes:", fp.evaluate("S.family.following[0].snapshot.meds.find(m=>m.name==='وارفارين').notes"))
    subprocess.run(['psql','-h','/var/tmp','-p','5499','-U','postgres','-d','dawa','-qc',"update fam_links set snapshot_at=snapshot_at-interval '10 min'; update fam_marks set updated_at=updated_at-interval '10 min'"])
    fp.evaluate("__net=[]"); fp.evaluate("famSync(true)"); fp.wait_for_timeout(800); fp.evaluate("famSync(true)"); fp.wait_for_timeout(800)
    print("follower later, nothing new (bytes):", [d for _, _, d in fp.evaluate("__net")], "| still has schedule:", len(fp.evaluate("S.family.following[0].snapshot.slots")))
    fp.evaluate("nativePause()"); print("bg flag:", fp.evaluate("appInBackground"))
    b.close()
finally:
    web.terminate(); api.terminate()
print("ERRORS:", errs)
