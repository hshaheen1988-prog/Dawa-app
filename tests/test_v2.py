import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__))); os.makedirs('shots', exist_ok=True)
import subprocess, time, json, sys
from playwright.sync_api import sync_playwright
srv = subprocess.Popen(["python3","-m","http.server","8781","-d", ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(1); errs=[]
stub = """
window.__calls = []; window.__pending = '[]';
window.DawaNative = {
  isNative(){return true}, version(){return 2},
  schedule(j){ window.__calls.push(['schedule', JSON.parse(j)]) },
  takePendingMarks(){ const p = window.__pending; window.__pending='[]'; return p },
  notifyNow(t,b){ window.__calls.push(['notify', t, b]) },
  testAlarm(j,s){ window.__calls.push(['test', JSON.parse(j), s]) },
  status(){ return JSON.stringify({notif:true, exact:false, fullScreen:true, battery:false, count:5, next: Date.now()+3600000, sdk:34, maker:'Xiaomi'}) },
  notificationsAllowed(){return true}, exactAlarmsAllowed(){return false},
  openNotificationSettings(){}, openExactAlarmSettings(){ window.__calls.push(['exact']) }, openFullScreenSettings(){}, openBatterySettings(){}, openAppSettings(){},
  share(t,c){ window.__calls.push(['share', t, c]) }
};"""
def run(native, lang, dark, tag):
  with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width":390,"height":844}, locale=lang, color_scheme="dark" if dark else "light", device_scale_factor=2)
    if native: ctx.add_init_script(stub)
    pg = ctx.new_page()
    pg.on("pageerror", lambda e: errs.append(f"[{tag}] PAGEERR "+str(e)))
    pg.on("console", lambda m: m.type=="error" and errs.append(f"[{tag}] "+m.text))
    pg.goto("http://localhost:8781/"); pg.wait_for_timeout(300)
    pg.screenshot(path=f"shots/{tag}_01_onboard.png")
    pg.fill("#obName","حمزة" if lang=="ar" else "Hamza"); pg.fill("#obPhone","0791234567"); pg.click("#onboardForm button[type=submit]")
    pg.wait_for_timeout(300); pg.screenshot(path=f"shots/{tag}_02_empty.png")
    now = time.localtime(); 
    def hm(delta_min):
      t = time.localtime(time.time()+delta_min*60); return f"{t.tm_hour:02d}:{t.tm_min:02d}"
    # Med 1: pattern 2,2,2.5 with 2 times (one past, one future), after food, stock
    pg.click(".fab"); pg.wait_for_timeout(300)
    pg.fill("#medForm [name=name]", "وارفارين" if lang=="ar" else "Warfarin")
    pg.click("#medForm .shape-pick button[data-shape=tablet]"); pg.click("#medForm .color-pick button:nth-child(3)")
    pg.click("#medForm .seg button[data-mode=pattern]")
    pg.fill(".p-amt","2"); pg.click("#medForm button:has-text('+')>> nth=0") if False else None
    pg.locator("#patternList + .add-link").click(); pg.locator("#patternList + .add-link").click()
    pg.locator(".p-amt").nth(2).fill("2.5")
    pg.locator("#timesList .t-in").first.fill(hm(-90))
    pg.locator("#timesList + .add-link").click(); pg.locator("#timesList .t-in").nth(1).fill(hm(120))
    pg.click("#medForm .opt[data-meal=after]")
    pg.locator("details.fold summary").last.click(); pg.fill("#medForm [name=stock]","12"); pg.fill("#medForm [name=lowAt]","4")
    pg.wait_for_timeout(200); pg.screenshot(path=f"shots/{tag}_03_form.png", full_page=True)
    pg.click("#medForm button[type=submit]"); pg.wait_for_timeout(400)
    # Med 2: daily capsule morning
    pg.click(".fab"); pg.fill("#medForm [name=name]", "أوميبرازول" if lang=="ar" else "Omeprazole")
    pg.locator("#timesList .t-in").first.fill(hm(-30)); pg.click("#medForm .opt[data-meal=empty]")
    pg.click("#medForm button[type=submit]"); pg.wait_for_timeout(300)
    # Med 3: PRN
    pg.click(".fab"); pg.fill("#medForm [name=name]", "بنادول" if lang=="ar" else "Panadol")
    pg.click("#medForm .seg button[data-mode=prn]"); pg.click("#medForm .shape-pick button[data-shape=oval]")
    pg.click("#medForm button[type=submit]"); pg.wait_for_timeout(300)
    pg.screenshot(path=f"shots/{tag}_04_today.png", full_page=True)
    # take first late dose
    pg.locator(".b-take").first.click(); pg.wait_for_timeout(500)
    pg.screenshot(path=f"shots/{tag}_05_taken.png", full_page=True)
    # menu -> snooze on next pending
    pg.locator(".b-more").first.click(); pg.wait_for_timeout(300)
    pg.screenshot(path=f"shots/{tag}_06_menu.png")
    pg.locator(".menu-item").nth(1).click(); pg.wait_for_timeout(300)
    # prn log
    pg.locator(".b-soft").first.click(); pg.wait_for_timeout(300)
    # week strip: previous day
    pg.locator(".wday").nth(2).click(); pg.wait_for_timeout(300); pg.screenshot(path=f"shots/{tag}_07b_daypopup.png"); pg.locator("#sheet .btn.soft").last.click() if pg.locator("#sheet .btn.soft").count() else pg.evaluate("closeModal()"); pg.wait_for_timeout(300)
    pg.screenshot(path=f"shots/{tag}_07_yesterday.png", full_page=True)
    pg.evaluate("closeModal(); pickDay(null)"); pg.wait_for_timeout(200)
    # meds tab
    pg.click("nav.tabs .tab[data-tab=meds]"); pg.wait_for_timeout(300); pg.screenshot(path=f"shots/{tag}_08_meds.png", full_page=True)
    # stats
    pg.click("nav.tabs .tab[data-tab=stats]"); pg.wait_for_timeout(300); pg.screenshot(path=f"shots/{tag}_09_stats.png", full_page=True)
    pg.locator(".card .b-soft").last.click(); pg.wait_for_timeout(200)
    pg.click("#sheet >> text=مشاركة كنص") if tag=="ar" else pg.click("#sheet >> text=Share as text"); pg.wait_for_timeout(200)
    pg.evaluate("closeModal()")
    # family
    pg.click("nav.tabs .tab[data-tab=family]"); pg.wait_for_timeout(200)
    pg.click(".fab"); pg.fill("#memberForm [name=name]", "أبوي" if lang=="ar" else "Dad"); pg.click("#memberForm button[type=submit]"); pg.wait_for_timeout(300)
    pg.click("nav.tabs .tab[data-tab=family]"); pg.wait_for_timeout(300); pg.screenshot(path=f"shots/{tag}_10_family.png", full_page=True)
    pg.locator(".chip").first.click(); pg.wait_for_timeout(200)
    # settings
    pg.click("#setBtn"); pg.wait_for_timeout(300); pg.screenshot(path=f"shots/{tag}_11_settings.png")
    if native:
      pg.locator("#sheet .set-row button").first.click(); pg.wait_for_timeout(300)
      pg.screenshot(path=f"shots/{tag}_12_checkup.png")
      pg.locator("#sheet .btn", has_text="10").click(); pg.wait_for_timeout(200)
      calls = pg.evaluate("window.__calls")
      sched = [c for c in calls if c[0]=='schedule'][-1][1]
      print(tag, "alarms:", len(sched)); 
      for a in sched[:3]: print("   ", time.strftime('%a %H:%M', time.localtime(a['at']/1000)), '|', a['person'], '|', a['body'].replace('\n',' / '), '| keys', a['keys'].count(',')+1, '|', a['timeLabel'])
      print(tag, "test alarm:", [c for c in calls if c[0]=='test'][-1][1]['body'])
      print(tag, "report shared:", [c for c in calls if c[0]=='share'][-1][2][:300].replace('\n',' / '))
      # pending mark from alarm screen
      pg.click("#sheet .grab"); pg.evaluate("closeModal()")
      pg.click("nav.tabs .tab[data-tab=today]")
      k = sched[0]['keys']
      pg.evaluate(f"window.__pending = JSON.stringify([{{keys: '{k}', at: Date.now()}}]); window.nativeResume()"); pg.wait_for_timeout(300)
      print(tag, "after pending mark, key taken:", pg.evaluate(f"load().log['{k.split(',')[0]}']?.status"))
      print(tag, "back:", pg.evaluate("nativeBack()"), pg.evaluate("nativeBack()"))
    b.close()
try:
  run(True, "ar", False, "ar")
  run(False, "en", True, "en_dark")
finally: srv.terminate()
print("ERRORS:", errs)
