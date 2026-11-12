"""NBT mínimo (leitura e escrita, gzip) para os ficheiros de estrutura do Minecraft. Sem dependências.

Tipos: os valores Python mapeiam assim — dict → Compound, list → List (tipo do 1.º elemento; lista vazia = End),
str → String, Byte/Short/Int/Long/Float/Double → os números com tipo explícito (classes abaixo), IntArray → Int Array.
"""
import gzip
import io
import struct


class Byte(int): pass
class Short(int): pass
class Int(int): pass
class Long(int): pass
class Float(float): pass
class Double(float): pass
class IntArray(list): pass
class ByteArray(list): pass
class LongArray(list): pass


def _tag_id(v):
    if isinstance(v, Byte): return 1
    if isinstance(v, Short): return 2
    if isinstance(v, Long): return 4
    if isinstance(v, Int) or (isinstance(v, int) and not isinstance(v, bool)): return 3
    if isinstance(v, Float): return 5
    if isinstance(v, Double) or isinstance(v, float): return 6
    if isinstance(v, ByteArray): return 7
    if isinstance(v, str): return 8
    if isinstance(v, IntArray): return 11
    if isinstance(v, LongArray): return 12
    if isinstance(v, list): return 9
    if isinstance(v, dict): return 10
    if isinstance(v, bool): return 1
    raise TypeError(type(v))


def _write(out, tid, v):
    if tid == 1: out.write(struct.pack(">b", int(v)))
    elif tid == 2: out.write(struct.pack(">h", v))
    elif tid == 3: out.write(struct.pack(">i", v))
    elif tid == 4: out.write(struct.pack(">q", v))
    elif tid == 5: out.write(struct.pack(">f", v))
    elif tid == 6: out.write(struct.pack(">d", v))
    elif tid == 7:
        out.write(struct.pack(">i", len(v))); out.write(bytes((b & 0xFF) for b in v))
    elif tid == 8:
        b = v.encode("utf-8"); out.write(struct.pack(">H", len(b))); out.write(b)
    elif tid == 9:
        et = _tag_id(v[0]) if v else 0
        out.write(struct.pack(">bi", et, len(v)))
        for e in v: _write(out, et, e)
    elif tid == 10:
        for k, e in v.items():
            t = _tag_id(e); out.write(struct.pack(">b", t)); _write(out, 8, k); _write(out, t, e)
        out.write(b"\x00")
    elif tid == 11:
        out.write(struct.pack(">i", len(v)))
        for e in v: out.write(struct.pack(">i", e))
    elif tid == 12:
        out.write(struct.pack(">i", len(v)))
        for e in v: out.write(struct.pack(">q", e))


def dumps(root):
    out = io.BytesIO()
    out.write(b"\x0a"); _write(out, 8, ""); _write(out, 10, root)
    return gzip.compress(out.getvalue(), mtime=0)


def _read(f, tid):
    if tid == 1: return Byte(struct.unpack(">b", f.read(1))[0])
    if tid == 2: return Short(struct.unpack(">h", f.read(2))[0])
    if tid == 3: return Int(struct.unpack(">i", f.read(4))[0])
    if tid == 4: return Long(struct.unpack(">q", f.read(8))[0])
    if tid == 5: return Float(struct.unpack(">f", f.read(4))[0])
    if tid == 6: return Double(struct.unpack(">d", f.read(8))[0])
    if tid == 7:
        n = struct.unpack(">i", f.read(4))[0]; return ByteArray(f.read(n))
    if tid == 8:
        n = struct.unpack(">H", f.read(2))[0]; return f.read(n).decode("utf-8")
    if tid == 9:
        et, n = struct.unpack(">bi", f.read(5)); return [_read(f, et) for _ in range(n)]
    if tid == 10:
        d = {}
        while True:
            t = struct.unpack(">b", f.read(1))[0]
            if t == 0: return d
            k = _read(f, 8); d[k] = _read(f, t)
    if tid == 11:
        n = struct.unpack(">i", f.read(4))[0]; return IntArray(struct.unpack(f">{n}i", f.read(4 * n)))
    if tid == 12:
        n = struct.unpack(">i", f.read(4))[0]; return LongArray(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(tid)


def loads(data):
    try:
        data = gzip.decompress(data)
    except OSError:
        pass
    f = io.BytesIO(data)
    assert f.read(1) == b"\x0a"
    _read(f, 8)
    return _read(f, 10)
