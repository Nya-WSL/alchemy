"""验证保护mini 与原版保护附魔「共存 + 叠加」。

1) 互斥规则：用 /enchant 走真实的 checkCompatibility 路径。
   /enchant 只对生物主手物品生效，所以让 husk 主手拿一件钻石胸甲，
   依次附上原版保护与保护mini —— 只要互斥，第二条命令就会报 incompatible。
2) 数值叠加：原版保护 IV（16% 减免）与保护mini 10 级（5.0 固定减免）
   应同时生效：10 点魔法伤害 -> 10 * 0.84 = 8.4，再减 5.0 = 3.4。

注意：第 2 项需要在非玩家生物上验证，脚本会临时把 miniProtectionPlayersOnly
改成 false，结束时恢复原值。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from rcon import execute

CONFIG = Path(r"D:\GitHub\alchemy\run\config\alchemy-common.toml")

TAG = 'Tags:["mp_test"]'
HAND_BASE = (
    '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:0b,'
    + TAG
    + ',HandItems:[{id:"minecraft:diamond_chestplate",Count:1b},{}]}'
)
ARMOR_BASE = (
    '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:0b,'
    + TAG
    + ',Attributes:[{Name:"generic.max_health",Base:200}],Health:200.0f'
)
KILL = "kill @e[tag=mp_test]"
SELECT = "@e[tag=mp_test,limit=1]"
HEALTH = f"data get entity {SELECT} Health"
FULL_HEALTH = 200.0


def parse_health(text: str) -> float | None:
    match = re.search(r"entity data:\s*([0-9.]+)f", text)
    return float(match.group(1)) if match else None


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

    failures = 0

    def check(name: str, ok: bool, detail: str = "") -> None:
        nonlocal failures
        failures += 0 if ok else 1
        print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"\n      {detail}" if detail else ""))

    # ---------- 1) 互斥规则：双向 ----------
    for title, order in (
        ("先附原版保护 IV，再附保护mini 10 级",
         [("minecraft:protection", 4), ("alchemy:mini_protection", 10)]),
        ("先附保护mini 10 级，再附原版保护 IV",
         [("alchemy:mini_protection", 10), ("minecraft:protection", 4)]),
    ):
        print("=" * 84)
        print(title)
        print("=" * 84)
        print("  summon ->", execute([KILL, f"summon minecraft:husk 0 -60 0 {HAND_BASE}"])[-1].strip())
        for index, (enchantment, level) in enumerate(order, start=1):
            out = execute([f"enchant {SELECT} {enchantment} {level}"])[0]
            print(f"  {index}) enchant {enchantment} {level} -> {out.strip()}")
        nbt = execute([f"data get entity {SELECT} HandItems[0].tag.Enchantments"])[0]
        print("  NBT:", nbt.strip())
        check(
            "两个附魔同时存在于同一件胸甲",
            "minecraft:protection" in nbt and "alchemy:mini_protection" in nbt,
            nbt.strip(),
        )
        print()

    # ---------- 2) 数值叠加 ----------
    print("=" * 84)
    print("数值叠加：原版保护 IV + 保护mini 10 级，10 点魔法伤害")
    print("=" * 84)

    original = CONFIG.read_text(encoding="utf-8")
    CONFIG.write_text(
        re.sub(r"(?m)^miniProtectionPlayersOnly\s*=\s*\w+$",
               "miniProtectionPlayersOnly = false", original),
        encoding="utf-8",
    )
    execute(["alchemy reload"])
    try:
        chest = (
            '{id:"minecraft:diamond_chestplate",Count:1b,tag:{Enchantments:['
            '{id:"minecraft:protection",lvl:4s},'
            '{id:"alchemy:mini_protection",lvl:10s}]}}'
        )
        execute([
            KILL,
            f"summon minecraft:husk 0 -60 0 {ARMOR_BASE},ArmorItems:["
            '{id:"minecraft:diamond_helmet",Count:1b},'
            + chest
            + ',{id:"minecraft:diamond_leggings",Count:1b},'
            '{id:"minecraft:diamond_boots",Count:1b}]}',
            f"damage {SELECT} 10 minecraft:magic",
        ])
        health = parse_health(execute([HEALTH])[0])
        execute([KILL])
    finally:
        CONFIG.write_text(original, encoding="utf-8")
        execute(["alchemy reload"])

    actual = FULL_HEALTH - health if health is not None else float("nan")
    # 10 * (1 - 0.16) = 8.4，再减 5.0 固定 = 3.4
    expected = 3.4
    print(f"  期望掉血 {expected:g}（10 x 0.84 - 5.0）  实测掉血 {actual:g}")
    check("百分比减免与固定减免叠加生效", abs(actual - expected) < 0.05)

    print()
    print("=" * 84)
    print("全部通过" if failures == 0 else f"{failures} 项不符合预期")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
