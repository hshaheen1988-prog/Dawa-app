import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__))); os.makedirs('shots', exist_ok=True)
import subprocess, time
from playwright.sync_api import sync_playwright
srv = subprocess.Popen(["python3","-m","http.server","8782","-d", ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(1); errs=[]
try:
  with sync_playwright() as p:
    b = p.chromium.launch(); ctx = b.new_context(viewport={"width":390,"height":844}, locale="ar", device_scale_factor=2)
    pg = ctx.new_page()
    pg.on("pageerror", lambda e: errs.append("PAGEERR "+str(e))); pg.on("console", lambda m: m.type=="error" and errs.append(m.text))
    pg.goto("http://localhost:8782/")
    pg.fill("#obName","حمزة"); pg.fill("#obPhone","0791234567"); pg.click("#onboardForm button[type=submit]")
    for name in ["وارفارين", "Diamox"]:
      pg.click(".fab"); pg.fill("#medForm [name=name]", name); pg.click("#medForm button[type=submit]"); pg.wait_for_timeout(200)
    # matching unit tests
    for q in ["بروفين","Brufen 400","فلاجيل","بنادول اكسترا","Aspirin","أسبرين","اسبوسيد","لازكس","توباماكس","ملوخية","Augmentin 1g","فيتامين د","Omeprazole","ثوم","Vitamin E","كونكور","Concor","ليثيوم","ديجوكسين","ميت"]:
      hits = pg.evaluate(f"interactionsFor({q!r}).map(e => e.t[0] + e.sev + ':' + e.label.en)")
      print(f"{q:14} -> {hits}")
    print("targetOf:", pg.evaluate("[targetOf('وارفارين 5 ملغ'), targetOf('Coumadin'), targetOf('ديموكس'), targetOf('أسيتازولاميد'), targetOf('بروفين')]"))
    # form warning
    pg.click(".fab"); pg.fill("#medForm [name=name]", "بروفين"); pg.wait_for_timeout(200)
    print("form warn:", pg.locator("#ixWarn").inner_text().replace("\n"," | "))
    pg.screenshot(path="shots/ix_1_form.png")
    pg.click("#medForm button[type=submit]"); pg.wait_for_timeout(200)
    pg.click("nav.tabs .tab[data-tab=meds]"); pg.wait_for_timeout(200); pg.screenshot(path="shots/ix_2_meds.png", full_page=True)
    pg.click(".ix-entry"); pg.wait_for_timeout(300); pg.fill("#ixQ","اسبر"); pg.wait_for_timeout(100)
    pg.locator("#ixSug .opt").first.click(); pg.wait_for_timeout(200)
    pg.screenshot(path="shots/ix_3_aspirin.png", full_page=True)
    pg.click("#sheet >> text=افحص كل أدويتي"); pg.wait_for_timeout(200)
    print("mine:", pg.locator("#ixRes").inner_text()[:200].replace("\n"," | "))
    pg.fill("#ixQ","كونكور"); pg.wait_for_timeout(100); print("none:", pg.locator("#ixRes").inner_text()[:120].replace("\n"," | "))
    b.close()
finally: srv.terminate()
print("ERRORS:", errs)
