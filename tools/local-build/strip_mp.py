# Remove MethodParameters attributes (debug metadata emitted by JDK 21 javac) from class files.
import struct, sys, pathlib
import sys
def names(b):
    n = struct.unpack('>H', b[8:10])[0]; i = 10; k = 1; m = {}
    SIZES = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while k < n:
        tag = b[i]
        if tag == 1:
            ln = struct.unpack('>H', b[i+1:i+3])[0]; m[k] = b[i+3:i+3+ln]; i += 3 + ln
        else: i += 1 + SIZES[tag]
        k += 2 if tag in (5, 6) else 1
    return i, m
def members(b, i, pool, drop):
    out = bytearray(); cnt = struct.unpack('>H', b[i:i+2])[0]; out += b[i:i+2]; i += 2; removed = 0
    for _ in range(cnt):
        out += b[i:i+6]; i += 6; ac = struct.unpack('>H', b[i:i+2])[0]; i += 2; attrs = []
        for _ in range(ac):
            ni, ln = struct.unpack('>HI', b[i:i+6]); a = b[i:i+6+ln]; i += 6 + ln
            if pool.get(ni) == drop: removed += 1
            else: attrs.append(a)
        out += struct.pack('>H', len(attrs)) + b''.join(attrs)
    return i, bytes(out), removed
total = 0
for p in pathlib.Path(sys.argv[1]).rglob('*.class'):
    b = p.read_bytes(); end, pool = names(b)
    i = end + 6; i += 2 + 2 * struct.unpack('>H', b[i:i+2])[0]
    j, fields, _ = members(b, i, pool, None)
    k, methods, r = members(b, j, pool, b'MethodParameters')
    if r: p.write_bytes(b[:i] + fields + methods + b[k:]); total += r
print('stripped MethodParameters:', total)
