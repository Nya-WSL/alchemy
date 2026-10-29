# alchemy

一个 **Minecraft 1.20.1 Forge** 模组，目前只包含一样东西：

## 点石成金（Transmutation）

用带「点石成金」的镐挖掘石头 / 圆石 / 深板岩 / 下界岩，有概率把掉落物换成原矿物品；
III 级起还有独立的概率额外掉一颗宝石。

**不再需要精准采集**，掉落物是被**替换**（挖石头不会再给圆石），而不是追加。

### 掉落流程

每次挖到可转化方块时按顺序走四步：

1. **骰概率** —— 按方块家族选表：主世界方块用 `overworldChancePerLevel`，下界岩用 `netherChancePerLevel`。
2. **抽物品** —— 命中后按权重从对应的物品池里抽一个（原矿物品，不是矿石方块）。
3. **算数量** —— 用原版 `ore_drops` 公式，时运 III 的期望倍率约 **2.2**，单次上限 4。
4. **追加宝石** —— 与第 1 步**完全独立**，即使转化没中也照掷；只有主世界表、且等级 ≥ `gemChanceStartLevel` 时才掷。

默认概率：

| 等级 | 主世界 | 下界 |
| --- | --- | --- |
| I | 0.5% | 0.5% |
| II | 1.0% | 1.0% |
| III | 2.0% | 2.0% |

### 默认物品池

- **主世界**（权重合计 94）：煤矿 30、粗铁 20、粗铜 15、粗金 10、红石 8、青金石 6、绿宝石 3、钻石 2
- **下界**（权重 9:1）：石英 9、远古残骸 1
- **宝石**（III 级起，2%）：红宝石 / 橙宝石 / 黄宝石 / 翡翠 / 蓝宝石 / 紫宝石，等概率

> 注意：主世界表里的「绿宝石」是原版 `minecraft:emerald`；宝石池里的「翡翠」是本模组的 `alchemy:jade`，两者不是一回事。

## 配置

`config/alchemy-common.toml`，改完执行 `/alchemy reload` 立即生效（无需重启）。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关，关掉后完全不干任何事 |
| `maxLevel` | `3` | 点石成金的最高等级 |
| `overworldChancePerLevel` | `[0.005, 0.010, 0.020]` | 主世界方块每级触发概率，超出表长按最后一项 |
| `netherChancePerLevel` | `[0.005, 0.010, 0.020]` | 下界方块每级触发概率 |
| `gemChanceStartLevel` | `3` | 从几级开始可能掉宝石 |
| `gemChance` | `0.02` | 每次挖掘掉宝石的概率 |
| `overworldOres` | 见文件 | 主世界物品池，格式 `物品ID#权重` |
| `netherOres` | 见文件 | 下界物品池，格式 `物品ID#权重` |
| `gems` | 见文件 | 宝石池，格式 `物品ID`（等概率） |
| `fortuneAffectsCount` | `true` | 时运是否影响掉落数量 |
| `autoDetectModOres` | `false` | 是否自动把模组矿石并入主世界物品池 |
| `autoDetectWeight` | `2` | 自动识别矿石的权重 |
| `autoDetectExclude` | 空 | 自动识别时要排除的物品 ID 关键字 |

写错的条目会**逐条警告并跳过**，不会静默退回默认值——这样配置错误不会被掩盖。

## 构建

```bash
./gradlew build          # 产物：build/libs/Alchemy-<version>.jar
./gradlew runServer      # 开发环境启动服务器
./gradlew test           # 纯计算逻辑的单元测试（24 个）
./gradlew runGameTestServer   # 游戏内行为测试（6 个）
```

## 验证脚本

`tools/rcon.py` 是一个极简 RCON 客户端，可以对一个开了 RCON 的开发服务器做端到端冒烟验证
（服务器需在 `run/server.properties` 里配好 `enable-rcon=true`）：

```bash
python tools/rcon.py "list" "time query gametime"
```

`tools/gen_test_structure.py` 生成 GameTest 用的结构模板 `transmutation_arena.nbt`
（3×3 石头地板；改布局后重跑它即可）。

## 相关的独立模组

战斗向附魔（**保护mini**、**双重打击**）已拆分到独立模组 `alchemy_soulforging`，
两者互不依赖，可以单独安装。
