"""生成 GameTest 用的结构模板（NBT）。

Minecraft 的结构模板就是「gzip 压缩过的 NBT」。仓库里没有 nbtlib，也不想为了
一个十几行的结构引入第三方依赖，所以这里直接用 struct 拼最小可用的 NBT 字节。

生成的布局（相对结构方块的坐标）：

    x 0..2, z 0..2 铺一层石头地板（y=0），上方留 2 格空气。

结构方块本身由 StructureUtils 放在 (0,-1,0)，模板原点会被摆到 (0,1,0)，
因此模板里的 y=0 就是地板顶面所在的那一层，实体站在 y=1。

用法：
    python tools/gen_test_structure.py
"""

import gzip
import pathlib
import struct

TAG_END = 0
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10

# 1.20.1 的 DataVersion；写上它，读档时就不会被 DataFixer 重跑一遍
DATA_VERSION = 3465

# 结构尺寸（x, y, z）
SIZE = (3, 3, 3)


def _string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _int(value: int) -> bytes:
    return struct.pack(">i", value)


def _list(elem_type: int, payloads) -> bytes:
    payloads = list(payloads)
    return bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def _compound(entries) -> bytes:
    out = bytearray()
    for name, tag_type, payload in entries:
        out.append(tag_type)
        out += _string(name)
        out += payload
    out.append(TAG_END)
    return bytes(out)


def build() -> bytes:
    size_x, size_y, size_z = SIZE

    palette = _list(TAG_COMPOUND, [
        _compound([("Name", TAG_STRING, _string("minecraft:stone"))]),
    ])

    blocks = []
    for x in range(size_x):
        for z in range(size_z):
            blocks.append(_compound([
                ("pos", TAG_LIST, _list(TAG_INT, [_int(x), _int(0), _int(z)])),
                ("state", TAG_INT, _int(0)),
            ]))

    root = _compound([
        ("size", TAG_LIST, _list(TAG_INT, [_int(size_x), _int(size_y), _int(size_z)])),
        ("entities", TAG_LIST, _list(TAG_COMPOUND, [])),
        ("blocks", TAG_LIST, _list(TAG_COMPOUND, blocks)),
        ("palette", TAG_LIST, palette),
        ("DataVersion", TAG_INT, _int(DATA_VERSION)),
    ])

    # 根 tag 的类型 + 空名字，然后是 compound 的内容
    return bytes([TAG_COMPOUND]) + _string("") + root


def main() -> None:
    target = pathlib.Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data" / "alchemy" / "structures" / "transmutation_arena.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    raw = build()
    target.write_bytes(gzip.compress(raw, 9))
    print("wrote %s (%d bytes uncompressed)" % (target, len(raw)))


if __name__ == "__main__":
    main()
