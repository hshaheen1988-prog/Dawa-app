# Adds two Android 14 (API 34) members to a local copy of android-33 android.jar (compile-time only):
#   NotificationManager.canUseFullScreenIntent()Z   and
#   Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT = "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"
import struct, zipfile, sys
SIZES = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}

def parse(b):
    n = struct.unpack('>H', b[8:10])[0]; i = 10; k = 1; utf = {}
    while k < n:
        tag = b[i]
        if tag == 1:
            ln = struct.unpack('>H', b[i+1:i+3])[0]; utf[b[i+3:i+3+ln].decode('utf-8', 'replace')] = k; i += 3 + ln
        else:
            i += 1 + SIZES[tag]
        k += 2 if tag in (5, 6) else 1
    return n, i, utf

def skip_members(b, i):
    cnt = struct.unpack('>H', b[i:i+2])[0]; i += 2
    for _ in range(cnt):
        i += 6; ac = struct.unpack('>H', b[i:i+2])[0]; i += 2
        for _ in range(ac):
            ln = struct.unpack('>I', b[i+2:i+6])[0]; i += 6 + ln
    return i

def add_consts(b, n, end, items):
    """items: list of raw constant entries (bytes). Returns new bytes and their indexes."""
    idx = []; blob = b''
    for it in items:
        idx.append(n); blob += it; n += 1
    return b[:8] + struct.pack('>H', n) + b[10:end] + blob + b[end:], idx, len(blob)

def utf8(s): e = s.encode(); return b'\x01' + struct.pack('>H', len(e)) + e

def add_method(b, name, desc, flags=0x0101):
    n, end, utf = parse(b)
    b, (ni, di), grow = add_consts(b, n, end, [utf8(name), utf8(desc)])
    i = end + grow + 6; i += 2 + 2 * struct.unpack('>H', b[i:i+2])[0]      # access/this/super, interfaces
    i = skip_members(b, i)                                                   # fields
    mc = struct.unpack('>H', b[i:i+2])[0]; j = skip_members(b, i)            # methods
    b = b[:i] + struct.pack('>H', mc + 1) + b[i+2:j] + struct.pack('>HHHH', flags, ni, di, 0) + b[j:]
    return b

def add_string_const(b, name, value):
    n, end, utf = parse(b)
    items = [utf8(name), utf8('Ljava/lang/String;'), utf8(value)]
    need_cv = 'ConstantValue' not in utf
    if need_cv: items.append(utf8('ConstantValue'))
    b, idx, grow = add_consts(b, n, end, items)
    ni, di, vi = idx[:3]; cvi = idx[3] if need_cv else utf['ConstantValue']
    b, (si,), grow2 = add_consts(b, n + len(items), end + grow, [b'\x08' + struct.pack('>H', vi)])
    i = end + grow + grow2 + 6; i += 2 + 2 * struct.unpack('>H', b[i:i+2])[0]
    fc = struct.unpack('>H', b[i:i+2])[0]; j = skip_members(b, i)
    field = struct.pack('>HHHH', 0x0019, ni, di, 1) + struct.pack('>HIH', cvi, 2, si)
    return b[:i] + struct.pack('>H', fc + 1) + b[i+2:j] + field + b[j:]

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zo:
    for info in zi.infolist():
        data = zi.read(info.filename)
        if info.filename == 'android/app/NotificationManager.class':
            data = add_method(data, 'canUseFullScreenIntent', '()Z')
        elif info.filename == 'android/provider/Settings.class':
            data = add_string_const(data, 'ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT', 'android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT')
        zo.writestr(info, data)
print('patched')
