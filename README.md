# alchemy

一个 **Minecraft 1.20.1 Forge** 模组，目前包含两样东西：

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
python tools/verify_coexistence.py       # 原版保护共存（双向）与数值叠加
```
