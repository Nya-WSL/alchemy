# alchemy

一个 **Minecraft 1.20.1 Forge** 模组，目前包含三样东西：

## 点石成金（Turn Stone into Gold）

配合**精准采集**，挖掘石头 / 圆石时有概率把掉落物变成矿石方块；挖深板岩时会换成对应的深板岩变种。
概率 = `chancePerLevel` x 附魔等级（默认每级 8%，III 级 24%）。

## 保护mini（Mini Protection）

每级**固定减少 0.5 点伤害**（1 点 = 半颗心），默认最高 10 级。
和原版「保护」不同，它的减伤是**确定性**的固定值，且**四件护甲上的等级会相加**——
满级 10 级 x 4 件 = 一次减伤 20 点。

- 适用部位：头盔 / 胸甲 / 护腿 / 靴子
- 与保护、火焰保护、摔落保护等原版附魔**不互斥，效果叠加**
- 减伤在原版护甲、保护类附魔、抗性药水之后结算；伤害被减到 0 时血量不变、不扣饥饿、不记战斗数据
  （但该次攻击仍会进入常规的 10 tick 无敌时间，这是固定减伤机制的固有结果）
- 默认只对**玩家**生效，可在配置里关掉这个限制

## 双重打击（Double Strike）

只对**剑**生效的武器附魔，最高 1 级。用剑命中后**立刻**再造成一次伤害，
数值是这次命中原始伤害的 50%，并额外多消耗一倍耐久（一次挥击扣 2 点）。

- 第二段伤害在**同一下游戏刻**结算：它用的伤害类型挂在 `bypasses_cooldown` 标签上，
  因此不会被受击者刚被打出来的 20 tick 无敌帧吃掉（不是「等无敌帧结束再打一下」）
- 只对**玩家造成的伤害**生效：弓箭、召唤物、摔落与火焰等一律不触发
- 一次挥击只触发一次；横扫命中多只目标时也只结算一次，不多扣耐久
- 没有开关，附上就生效；仍然需要剑（斧、三叉戟、空手都不行）

## 配置

`config/alchemy-common.toml`，改完执行 `/alchemy reload` 立即生效（无需重启）。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `maxLevel` | `3` | 点石成金的最高等级 |
| `chancePerLevel` | `0.08` | 点石成金每级触发概率 |
| `transmuteAllDrops` | `false` | 是否转化该方块的所有掉落物 |
| `ores` | 见文件 | 可转化出的矿石方块及权重，格式 `方块ID#权重` |
| `autoDetectModOres` | `true` | 是否自动把模组矿石并入矿石池 |
| `autoDetectWeight` | `2` | 自动识别矿石的权重 |
| `autoDetectExclude` | 空 | 自动识别时要排除的方块 ID 关键字 |
| `miniProtectionMaxLevel` | `10` | 保护mini 的最高等级 |
| `miniProtectionReductionPerLevel` | `0.5` | 保护mini 每级固定减伤点数 |
| `miniProtectionPlayersOnly` | `true` | 保护mini 是否只对玩家生效 |

## 构建

```bash
./gradlew build          # 产物：build/libs/Alchemy-<version>.jar
./gradlew runServer      # 开发环境启动服务器
./gradlew test           # 纯计算逻辑的单元测试
```

## 验证脚本

`tools/` 下有几个脚本，可以对一个开了 RCON 的开发服务器做端到端验证
（服务器需在 `run/server.properties` 里配好 `enable-rcon=true`）：

```bash
python tools/verify_mini_protection.py   # 12 项：等级相加、固定减伤、玩家开关
python tools/verify_double_strike.py     # 4 组：bypasses_cooldown 连续结算、最高 1 级、全剑类可附、非剑拒绝
python tools/verify_coexistence.py       # 原版保护共存（双向）与数值叠加
```

### GameTest

「双重打击」只在「玩家本人用近战武器直接造成伤害」时触发，而 RCON 执行 `/damage` 时伤害来源
没有 `Entity`，走不进这条分支——所以这条附魔的行为只能靠 GameTest 证明：

```bash
./gradlew runGameTestServer   # 跑完即退出，适合 CI
```

用例位于 `src/main/java/com/nya_wsl/alchemy/gametest/DoubleStrikeGameTest.java`，共 4 个：

| 用例 | 守的是什么 |
| --- | --- |
| `bonusDamageLandsInSameTick` | 总伤害正好是白板剑的 1.5 倍，且 1 tick 后就能看到（即第二段同 tick 落地） |
| `extraDurabilityConsumedPerSwing` | 一次挥击扣 2 点耐久（本体 1 + 双重打击 1） |
| `bonusAppliesExactlyOnce` | 第二段只结算一次，隔 20 tick 不会继续掉血 |
| `nonPlayerDamageIsUnaffected` | 非玩家来源的伤害不加倍 |

竞技场模板是 `src/main/resources/data/alchemy/structures/double_strike_arena.nbt`，
用 `python tools/gen_test_structure.py` 生成、`python tools/check_test_structure.py` 校验
（不依赖 `nbtlib`，直接拼 NBT 字节再 gzip）。

