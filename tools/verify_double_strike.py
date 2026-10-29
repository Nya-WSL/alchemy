"""验证「双重打击」附魔在真实服务器上的表现。

脚本覆盖 RCON 能验证的 4 组事实：

1. 伤害类型 alchemy:double_strike 已由数据包注册成功，并且挂在
   minecraft:bypasses_cooldown 标签上 —— 同一目标、同一无敌帧窗口内连打两次，
   两次都全额结算；作为对照，原版 minecraft:magic 在同样窗口内会被判 "invulnerable"。
2. /enchant 走真实的 canEnchant 路径：剑可以附，最高 1 级（附 2 级报超等级）。
3. 只对剑生效：镐、斧、三叉戟、弓、盾、盔甲等一律 "cannot support that enchantment"。
4. 附魔等级正确写入物品 NBT。

玩家造成的伤害才会触发第二段 —— 那部分只能在 GameTest 里验证
（RCON 命令源没有 Entity，打出的伤害没有玩家归属），见
src/test ... DoubleStrikeGameTest。

用法：先 runServer 起服，然后 python tools/verify_double_strike.py
"""

from __future__ import annotations

import re
import sys

from rcon import execute

BASE = (
    '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:0b,'
    'Tags:["ds_test"],Attributes:[{Name:"generic.max_health",Base:200}],Health:200.0f}'
)
HAND = (
    '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:0b,'
    'Tags:["ds_hand"],HandItems:[{id:"minecraft:%s",Count:1b},{}]}'
)
KILL = "kill @e[tag=ds_test]"
KILL_HAND = "kill @e[tag=ds_hand]"
TARGET = "@e[tag=ds_test,limit=1]"
HAND_HOLDER = "@e[tag=ds_hand,limit=1]"
FULL_HEALTH = 200.0

# 每一条都是「绝不该被附上」的物品
REJECTED_ITEMS = [
    ("diamond_pickaxe", "镐"),
    ("diamond_axe", "斧"),
    ("diamond_shovel", "锹"),
    ("trident", "三叉戟"),
    ("bow", "弓"),
    ("shield", "盾牌"),
    ("diamond_helmet", "头盔"),
]

# 所有剑类都应接受
ACCEPTED_SWORDS = [
    ("wooden_sword", "木剑"),
    ("stone_sword", "石剑"),
    ("iron_sword", "铁剑"),
    ("golden_sword", "金剑"),
    ("diamond_sword", "钻石剑"),
    ("netherite_sword", "下界合金剑"),
]


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
        indent = "      "
        print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"\n{indent}{detail}" if detail else ""))

    def banner(title: str) -> None:
        print("=" * 84)
        print(title)
        print("=" * 84)

    # ---------- 1) 伤害类型注册 + bypasses_cooldown ----------
    banner("1) 连续两次同一伤害类型：应当都被结算（bypasses_cooldown + 不递减）")
    out = execute([
        KILL,
        f"summon minecraft:husk 0 -60 0 {BASE}",
        f"damage {TARGET} 10 alchemy:double_strike",
        f"data get entity {TARGET} Health",
        f"damage {TARGET} 10 alchemy:double_strike",
        f"data get entity {TARGET} Health",
    ])
    print("  第一刀 ->", out[2].strip())
    print("  血量   ->", out[3].strip())
    print("  第二刀 ->", out[4].strip())
    print("  血量   ->", out[5].strip())

    first_hit_ok = "Applied 10.0 damage" in out[2]
    second_hit_ok = "Applied 10.0 damage" in out[4]
    check("伤害类型 alchemy:double_strike 已被数据包注册", first_hit_ok, out[2].strip())

    health_after_first = parse_health(out[3])
    health_after_second = parse_health(out[5])
    first_drop = FULL_HEALTH - health_after_first if health_after_first is not None else float("nan")
    second_drop = health_after_first - health_after_second if (health_after_first is not None and health_after_second is not None) else float("nan")
    print(f"     第一刀掉血 {first_drop:g}，第二刀掉血 {second_drop:g}")
    check("无敌帧内第二刀照常全额结算", second_hit_ok, out[4].strip())
    check("两刀伤害完全相同（无 lastHurt 递减）", abs(first_drop - second_drop) < 0.01,
          f"{first_drop:g} vs {second_drop:g}")

    # 对照组
    control = execute([f"damage {TARGET} 10 minecraft:magic"])
    print("  对照 minecraft:magic ->", control[0].strip())
    check("对照：原版伤害类型在无敌帧内被拦下",
          "invulnerable" in control[0].lower(), control[0].strip())
    execute([KILL])
    print()

    # ---------- 2) 剑可以附，最高 1 级 ----------
    banner("2) 剑可附魔，且最高只到 1 级")
    out = execute([
        KILL_HAND,
        f"summon minecraft:husk 0 -60 0 {HAND % 'diamond_sword'}",
        f"enchant {HAND_HOLDER} alchemy:double_strike 1",
        f"enchant {HAND_HOLDER} alchemy:double_strike 2",
        f"data get entity {HAND_HOLDER} HandItems[0].tag.Enchantments",
        KILL_HAND,
    ])
    print("  enchant 1 级 ->", out[2].strip())
    print("  enchant 2 级 ->", out[3].strip())
    print("  NBT          ->", out[4].strip())
    check("钻石剑可以附上双重打击", "Applied enchantment" in out[2], out[2].strip())
    check("2 级被拒绝（最高 1 级）", "maximum level of 1" in out[3], out[3].strip())
    check("附魔正确写入物品 NBT",
          "alchemy:double_strike" in out[4] and "lvl: 1s" in out[4], out[4].strip())
    print()

    # ---------- 3) 其余剑类 ----------
    banner("3) 全部剑类都应接受该附魔")
    for item, label in ACCEPTED_SWORDS:
        result = execute([
            KILL_HAND,
            f"summon minecraft:husk 0 -60 0 {HAND % item}",
            f"enchant {HAND_HOLDER} alchemy:double_strike 1",
            KILL_HAND,
        ])[2]
        check(f"{label}（{item}）可附魔", "Applied enchantment" in result, result.strip())
    print()

    # ---------- 4) 非剑物品一律拒绝 ----------
    banner("4) 非剑物品一律拒绝：只对剑生效")
    for item, label in REJECTED_ITEMS:
        result = execute([
            KILL_HAND,
            f"summon minecraft:husk 0 -60 0 {HAND % item}",
            f"enchant {HAND_HOLDER} alchemy:double_strike 1",
            KILL_HAND,
        ])[2]
        check(f"{label}（{item}）不可附魔", "cannot support that enchantment" in result,
              result.strip())

    execute([KILL, KILL_HAND])
    print()
    print("=" * 84)
    print("全部通过" if failures == 0 else f"{failures} 项不符合预期")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

