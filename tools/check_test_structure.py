"""把 double_strike_arena.nbt 解压并解析回来，确认结构模板内容正确。

只用于人工核对生成结果，不参与构建。用法：
    python tools/check_test_structure.py
"""

import gzip
import pathlib
import struct
import sys

TAG_END = 0
TAG_BYTE = 1
TAG_INT = 3
TAG_LONG = 4
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, n: int) -> bytes:
        chunk = self.data[self.pos:self.pos + n]
        if len(chunk) != n:
            raise ValueError("数据在偏移 %d 处提前结束" % self.pos)
        self.pos += n
        return chunk

    def u8(self) -> int:
        return self.take(1)[0]

    def i32(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def i64(self) -> int:
        return struct.unpack(">q", self.take(8))[0]

    def string(self) -> str:
        return self.take(struct.unpack(">H", self.take(2))[0]).decode("utf-8")

    def payload(self, tag_type: int):
        if tag_type == TAG_BYTE:
            return self.u8()
        if tag_type == TAG_INT:
            return self.i32()
        if tag_type == TAG_LONG:
            return self.i64()
        if tag_type == TAG_STRING:
            return self.string()
        if tag_type == TAG_LIST:
            elem = self.u8()
            count = self.i32()
            return [self.payload(elem) for _ in range(count)]
        if tag_type == TAG_COMPOUND:
            out = {}
            while True:
                child = self.u8()
                if child == TAG_END:
                    return out
                name = self.string()
                out[name] = self.payload(child)
        raise ValueError("不支持的 tag 类型 %d" % tag_type)


def main() -> int:
    path = pathlib.Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data" / "alchemy" / "structures" / "double_strike_arena.nbt"
    compressed = path.read_bytes()
    raw = gzip.decompress(compressed)
    print("压缩后 %d 字节，解压后 %d 字节" % (len(compressed), len(raw)))

    reader = Reader(raw)
    root_type = reader.u8()
    root_name = reader.string()
    root = reader.payload(root_type)
    print("根 tag 类型=%d 名字=%r，剩余未读字节=%d" % (root_type, root_name, len(raw) - reader.pos))

    failures = []

    if root_type != TAG_COMPOUND:
        failures.append("根 tag 应为 TAG_Compound(10)，实际 %d" % root_type)
    if root_name != "":
        failures.append("根 tag 名字应为空，实际 %r" % root_name)
    if reader.pos != len(raw):
        failures.append("解析结束时仍有 %d 字节未消费" % (len(raw) - reader.pos))

    size = root.get("size")
    print("size =", size)
    if size != [5, 3, 5]:
        failures.append("size 应为 [5, 3, 5]，实际 %r" % (size,))

    palette = root.get("palette")
    print("palette =", palette)
    if palette != [{"Name": "minecraft:stone"}]:
        failures.append("palette 应只含 minecraft:stone，实际 %r" % (palette,))

    blocks = root.get("blocks", [])
    print("blocks 数量 =", len(blocks))
    if len(blocks) != 25:
        failures.append("blocks 应为 25 个（5x5 地板），实际 %d" % len(blocks))

    seen = set()
    for entry in blocks:
        pos = entry.get("pos")
        if entry.get("state") != 0:
            failures.append("方块 %r 的 state 应为 0" % (pos,))
        if pos is None or len(pos) != 3:
            failures.append("方块坐标格式不对：%r" % (entry,))
            continue
        seen.add(tuple(pos))

    expected = {(x, 0, z) for x in range(5) for z in range(5)}
    if seen != expected:
        failures.append("方块坐标集合不符，多出 %r，缺少 %r" % (sorted(seen - expected), sorted(expected - seen)))

    entities = root.get("entities")
    print("entities =", entities)
    if entities != []:
        failures.append("entities 应为空列表，实际 %r" % (entities,))

    data_version = root.get("DataVersion")
    print("DataVersion =", data_version)
    if data_version != 3465:
        failures.append("DataVersion 应为 3465，实际 %r" % (data_version,))

    print("顶层键 =", sorted(root.keys()))

    if failures:
        print("\n失败：")
        for failure in failures:
            print(" -", failure)
        return 1

    print("\n结构模板校验通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
