import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__))); os.makedirs('shots', exist_ok=True)
import subprocess, time
from playwright.sync_api import sync_playwright
srv = subprocess.Popen(["python3","-m","http.server","8784","-d", ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(1); errs=[]
try:
  with sync_playwright() as p:
    b = p.chromium.launch(); ctx = b.new_context(viewport={"width":390,"height":844}, locale="ar", device_scale_factor=2)
    pg = ctx.new_page(); pg.on("pageerror", lambda e: errs.append(str(e))); pg.on("console", lambda m: m.type=="error" and errs.append(m.text))
    pg.goto("http://localhost:8784/")
    pg.fill("#obName","حمزة"); pg.fill("#obPhone","0791234567"); pg.click("#onboardForm button[type=submit]")
    # seed data directly: meds + history + INR
    pg.evaluate("""() => {
      const td = today(), pid = S.active;
      S.meds.push({id:'w1',profileId:pid,name:'وارفارين',unit:'حبة',times:['20:00'],start:addDays(td,-40),end:'',schedule:{mode:'pattern',pattern:[2,2,2.5],rest:0},stages:[{from:addDays(td,-40),amount:1}],notes:'',color:COLORS[2],shape:'tablet',meal:'after'});
      S.meds.push({id:'a1',profileId:pid,name:'أسيتازولاميد',unit:'حبة',times:['08:00','20:00'],start:addDays(td,-40),end:'',schedule:{mode:'daily'},stages:[{from:addDays(td,-40),amount:1}],notes:'مع كوب ماء كامل',color:COLORS[1],shape:'capsule',meal:'after'});
      S.meds.push({id:'b1',profileId:pid,name:'بروفين',unit:'حبة',times:['14:00'],start:addDays(td,-5),end:'',schedule:{mode:'daily'},stages:[{from:addDays(td,-5),amount:1}],notes:'',color:COLORS[4],shape:'oval',meal:'with'});
      for (let i=1;i<30;i++){ const d=addDays(td,-i); for (const s of slotsFor(pid,d)) if (Math.random()<0.88) S.log[s.key]={status:'taken',at:new Date(d+'T'+s.time+':00').toISOString()}; }
      const r = inrOf(pid); [[-49,1.8,'5 ملغ يومياً',false],[-35,2.3,'5.5 ملغ يومياً',true],[-21,3.4,'5.5 ملغ يومياً',false],[-14,2.9,'5 ملغ يومياً',true],[-7,2.5,'5 ملغ يومياً',false],[0,2.4,'5 ملغ يومياً',false]].forEach(([o,v,dz,c])=>r.tests.push({id:uid(),date:addDays(td,o),value:v,dose:dz,changed:c,note:''}));
      r.next = addDays(td, 14); save(); render();
    }""")
    html = pg.evaluate("reportHtml({days:30, labs:true})")
    p2 = ctx.new_page(); p2.set_content(html); p2.wait_for_timeout(300)
    p2.pdf(path="report_test.pdf", format="A4", print_background=True)
    p2.set_viewport_size({"width":794,"height":1123}); p2.screenshot(path="shots/r_report.png", full_page=True)
    pg.click("nav.tabs .tab[data-tab=stats]"); pg.wait_for_timeout(200); pg.locator(".card .b-soft").last.click(); pg.wait_for_timeout(200)
    pg.screenshot(path="shots/r_sheet.png")
    print(pg.evaluate("buildReport({days:30,labs:true})"))
    print("---- no labs line count:", pg.evaluate("buildReport({days:30,labs:false}).split('\\n').length"))
    b.close()
finally: srv.terminate()
print("ERRORS:", errs)
