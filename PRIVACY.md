# سياسة الخصوصية — تطبيق دوائي (Dawa)

آخر تحديث: 24 أيلول 2026

## باختصار
- كل بياناتك محفوظة **على تلفونك فقط**.
- ما في إعلانات، ولا تتبّع، ولا بيع بيانات لأي جهة.
- البيانات بتطلع من تلفونك **بس إذا إنت فعّلت "المتابعة العائلية"**، وبس الأدوية اللي بتختار تشاركها.

## البيانات اللي على تلفونك
اسمك ورقمك، ملفات أفراد العائلة، الأدوية ومواعيدها، سجل الجرعات، قراءات INR، والإعدادات.
هاي البيانات ما بتنرسل لأي مكان. التقرير (PDF) والتنبيه الصوتي بيتعملوا على التلفون نفسه.

## المتابعة العائلية (اختيارية)
لما تفعّلها، التطبيق بيرسل لسيرفر (خدمة Supabase):
- **المريض:** اسمه، رقم تلفونه، الأدوية اللي اختار يشاركها (الاسم، الشكل، الملاحظات، اسم الشخص)، جدول مواعيدها لأسبوع قبل وأسبوعين بعد، وحالة كل جرعة (أُخذت / تخطّاها).
- **المتابع:** اسمه ورقم تلفونه.

الوصول للبيانات بيكون فقط بكود المشاركة، وكل جهاز له مفتاح سري خاص فيه. الجداول مقفلة وما حدا بيقدر يقرأها مباشرة.

**مدة الاحتفاظ:**
- حالة الجرعات: بتنحذف تلقائياً بعد 60 يوم.
- لما المريض يوقف المشاركة: الأدوية المشتركة والسجل وقائمة المتابعين بينحذفوا فوراً.
- الأجهزة اللي ما استخدمت المشاركة 180 يوم: بتنحذف مع كل بياناتها.

## الأذونات وليش
| الإذن | السبب |
|---|---|
| الإشعارات، المنبّه الدقيق، الشاشة الكاملة | تذكير الجرعة بوقتها وعلى شاشة القفل |
| التشغيل بعد إعادة التشغيل | إعادة ضبط المنبّهات بعد إطفاء التلفون |
| الإنترنت | المتابعة العائلية فقط |
| تجاهل توفير البطارية | حتى ما يوقف النظام المنبّه |

## تنبيه طبي
التطبيق للتذكير والتنظيم فقط. قائمة التفاعلات الدوائية مش شاملة، وما بتغني عن استشارة الطبيب أو الصيدلي.

## حذف بياناتك
- على تلفونك: الإعدادات ← حذف كل البيانات، أو احذف التطبيق.
- على السيرفر: أوقف المشاركة من تبويب العائلة. لأي طلب ثاني افتح طلب هون: https://github.com/hshaheen1988-prog/Dawa-app/issues

---

# Privacy Policy — Dawa

Last updated: 24 September 2026

## In short
- All your data is stored **on your phone only**.
- No ads, no tracking, no selling data.
- Data leaves your phone **only if you turn on Family sharing**, and only for the medicines you choose to share.

## Data on your phone
Your name and phone number, family profiles, medicines and schedules, dose history, INR readings and settings. None of it is sent anywhere. The PDF report and voice reminders are produced on the device.

## Family sharing (optional)
When turned on, the app sends to a server (Supabase):
- **Patient:** name, phone number, the medicines they chose to share (name, shape, notes, person), their schedule from one week back to two weeks ahead, and each dose's status (taken / skipped).
- **Follower:** name and phone number.

Access requires the share code, and each device has its own secret key. Tables are locked and cannot be read directly.

**Retention:** dose statuses are deleted after 60 days; stopping sharing deletes the shared medicines, history and followers immediately; devices that have not used sharing for 180 days are deleted with their data.

## Permissions
Notifications, exact alarms and full-screen alerts: to remind you on time, even on the lock screen. Run at startup: to restore alarms after a reboot. Internet: family sharing only. Ignore battery optimisation: so the system does not stop the alarm.

## Medical notice
The app is a reminder and organising tool. The interaction list is not complete and is not a substitute for a doctor or pharmacist.

## Deleting your data
On the phone: Settings → Delete all data, or uninstall the app. On the server: stop sharing in the Family tab. For anything else, open a request at https://github.com/hshaheen1988-prog/Dawa-app/issues
