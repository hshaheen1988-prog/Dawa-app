import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__))); os.makedirs('shots', exist_ok=True)
import subprocess, time, json
from playwright.sync_api import sync_playwright
srv = subprocess.Popen(["python3","-m","http.server","8796","-d", ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(1); errs=[]
stub = """window.__saved=null; window.__shared=null; window.DawaNative={isNative(){return true},version(){return 4},schedule(){},setFamily(){},setWidget(){},
 takePendingMarks(){return '[]'},status(){return '{}'},notificationsAllowed(){return true},exactAlarmsAllowed(){return true},
 saveFile(n,c){window.__saved=[n,c];return true}, share(t,c){window.__shared=[t,c]}};"""
with sync_playwright() as p:
  b=p.chromium.launch()
  # phone A: data + backup
  ca=b.new_context(viewport={"width":390,"height":844},locale="ar"); ca.add_init_script(stub); pa=ca.new_page(); pa.on("pageerror",lambda e:errs.append("A "+str(e)))
  pa.goto("http://localhost:8796/"); pa.fill("#obName","حمزة"); pa.fill("#obPhone","0791234567"); pa.click("#onboardForm button[type=submit]"); pa.wait_for_timeout(300)
  pa.click(".fab"); pa.fill("#medForm [name=name]","وارفارين"); pa.click("#medForm button[type=submit]"); pa.wait_for_timeout(300)
  pa.evaluate("inrOf(S.active).tests.push({date:'2026-09-01',value:2.6}); save()")
  pa.evaluate("exportData()"); pa.wait_for_timeout(200); pa.screenshot(path="shots/b_export.png")
  pa.click("#sheet >> text=حفظ ملف في التنزيلات"); pa.wait_for_timeout(300)
  name, content = pa.evaluate("__saved"); toast = pa.inner_text("#toast") if pa.locator("#toast").count() else ""
  print("saved file:", name, len(content), "| toast:", toast.strip()[:60])
  pa.evaluate("exportData()"); pa.click("#sheet >> text=مشاركة"); print("share fallback ok:", pa.evaluate("__shared")[0])
  # phone B: fresh install → restore from onboarding by pasting (old app shared plain text)
  cb=b.new_context(viewport={"width":390,"height":844},locale="ar"); cb.add_init_script(stub); pb=cb.new_page(); pb.on("pageerror",lambda e:errs.append("B "+str(e)))
  pb.goto("http://localhost:8796/"); pb.wait_for_timeout(300); pb.screenshot(path="shots/b_onboard.png")
  pb.click("text=عندك نسخة احتياطية؟"); pb.wait_for_timeout(200); pb.screenshot(path="shots/b_import.png")
  pb.fill("#importText", "dawa-backup-2026-09-24.json\n" + content + "\n"); pb.click("#sheet .btn.ghost"); pb.wait_for_timeout(500)
  print("restored:", pb.evaluate("[S.user.name, S.meds.map(m=>m.name), inrOf(S.active).tests.length]"), "| onboarding hidden:", pb.evaluate("getComputedStyle(document.getElementById('onboard')||document.body).display"))
  pb.screenshot(path="shots/b_restored.png")
  # bad paste
  pb.evaluate("openImport()"); pb.fill("#importText","hello"); pb.click("#sheet .btn.ghost"); pb.wait_for_timeout(300)
  print("bad paste keeps data:", pb.evaluate("S.meds.length"))
  # file import over existing data → confirm dialog
  open('bk.json','w').write(content)
  pb.evaluate("openImport()"); pb.set_input_files("#importFile","bk.json"); pb.wait_for_timeout(300)
  print("confirm shown:", pb.is_visible("#confirm"), pb.inner_text("#confirmMsg")[:40]); pb.click("#confirmYes"); pb.wait_for_timeout(300)
  print("after file import:", pb.evaluate("S.meds.map(m=>m.name)"))
  b.close()
srv.terminate(); print("ERRORS:", errs)
