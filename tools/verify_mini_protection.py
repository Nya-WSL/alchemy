"""保护mini 端到端验证：在开发服务器上用 RCON 实测减伤。

分两个阶段，验证「玩家开关」和「每级 0.5 固定减伤 / 四件等级相加」：

- A 阶段：miniProtectionPlayersOnly = true（默认）。husk 不是玩家 → 完全不该减伤。
- B 阶段：改成 false 并 /alchemy reload。同一套用例 → 应当开始按等级减伤。

伤害统一用 minecraft:magic（bypasses_armor，不吃护甲值和抗性），
这样掉血量只由保护mini 决定，可以干净地反推出固定减伤量。

用法：python tools/verify_mini_protection.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from rcon import execute

CONFIG = Path(r"D:\GitHub\alchemy\run\config\alchemy-common.toml")

TAG = 'Tags:["mp_test"]'
BASE = (
    '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:0b,'
    + TAG
    + ',Attributes:[{Name:"generic.max_health",Base:200}],Health:200.0f'
)

KILL = "kill @e[tag=mp_test]"
HEALTH = "data get entity @e[tag=mp_test,limit=1] Health"
RCD = "alchemy reload"

# 基线血量，用来算掉血量
FULL_HEALTH = 200.0


def piece(item: str, level: int | None) -> str:
    if level is None:
        return f'{{id:"minecraft:{item}",Count:1b}}'
    return (
        f'{{id:"minecraft:{item}",Count:1b,tag:{{Enchantments:['
        f'{{id:"alchemy:mini_protection",lvl:{level}s}}]}}}}'
    )


def summon(levels: dict[str, int | None]) -> str:
    slots = ",".join(
        [
            piece("diamond_helmet", levels.get("head")),
            piece("diamond_chestplate", levels.get("chest")),
            piece("diamond_leggings", levels.get("legs")),
            piece("diamond_boots", levels.get("feet")),
        ]
    )
    return f"summon minecraft:husk 0 -60 0 {BASE},ArmorItems:[{slots}]}}"


def parse_health(text: str) -> float | None:
    match = re.search(r"entity data:\s*([0-9.]+)f", text)
    return float(match.group(1)) if match else None


def set_players_only(value: bool) -> None:
    text = CONFIG.read_text(encoding="utf-8")
    updated = re.sub(
        r"(?m)^miniProtectionPlayersOnly\s*=\s*\w+$",
        f"miniProtectionPlayersOnly = {'true' if value else 'false'}",
        text,
    )
    if updated == text and f"miniProtectionPlayersOnly = {'true' if value else 'false'}" not in text:
        raise RuntimeError("miniProtectionPlayersOnly 未写回，检查配置文件")
    CONFIG.write_text(updated, encoding="utf-8")


# (用例名, 各部位等级, 施加伤害, 生效时应掉的血量)
CASES: list[tuple[str, dict[str, int | None], int, float]] = [
    ("白板四件（基线）", {"head": None, "chest": None, "legs": None, "feet": None}, 10, 10.0),
    ("单件 10 级", {"head": None, "chest": 10, "legs": None, "feet": None}, 10, 5.0),
    ("四件各 1 级（共 4 级）", {"head": 1, "chest": 1, "legs": 1, "feet": 1}, 10, 8.0),
    ("四件各 10 级（共 40 级）", {"head": 10, "chest": 10, "legs": 10, "feet": 10}, 10, 0.0),
    ("四件各 10 级 / 30 伤害", {"head": 10, "chest": 10, "legs": 10, "feet": 10}, 30, 10.0),
    ("两件 5 级（共 10 级）", {"head": 5, "chest": 5, "legs": None, "feet": None}, 10, 5.0),
]


def measure(levels: dict[str, int | None], damage: int) -> float:
    """返回这次伤害实际掉的血量。"""
    execute(
        [
            KILL,
            summon(levels),
            f"damage @e[tag=mp_test,limit=1] {damage} minecraft:magic",
        ]
    )
    health = parse_health(execute([HEALTH])[0])
    execute([KILL])
    if health is None:
        return float("nan")
    return FULL_HEALTH - health


def main() -> int:
    # Windows 控制台默认 GBK，避免中文/符号编码炸掉
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

    print(f"配置文件：{CONFIG}\n")

    failures = 0
    stages = [
        ("A 阶段  miniProtectionPlayersOnly = true（husk 不是玩家 → 不减伤）", True, False),
        ("B 阶段  miniProtectionPlayersOnly = false（husk 也减伤）", False, True),
    ]

    for title, players_only, active in stages:
        set_players_only(players_only)
        print("=" * 84)
        print(title)
        print("=" * 84)
        execute([RCD], echo=True)

        for label, levels, damage, drop_if_active in CASES:
            expected = drop_if_active if active else float(damage)
            actual = measure(levels, damage)
            ok = abs(actual - expected) < 0.05
            if not ok:
                failures += 1
            print(
                f"{'PASS' if ok else 'FAIL'}  {label:<24} 伤害 {damage:>2}  "
                f"期望掉血 {expected:>5.1f}  实测 {actual:>5.1f}"
            )
        print()

    # 恢复默认值，避免污染仓库里的默认配置
    set_players_only(True)
    execute([RCD])

    print("=" * 84)
    print("全部通过" if failures == 0 else f"{failures} 项不符合预期")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
