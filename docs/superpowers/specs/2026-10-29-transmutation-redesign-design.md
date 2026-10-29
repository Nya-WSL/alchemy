# 点石成金附魔重做（依据《点石成金_数值设计_v2.md》第 1 节）

日期：2026-10-29
状态：设计定稿，待实现

## 1. 这次要改成什么

现有实现只做一件事：**带精准采集的镐挖石头，掉落物被替换成矿石方块**。
新设计把它换成一个**有等级阶梯、分主世界/下界两张表、还会掉宝石**的掉矿附魔。
两者不是同一套机制，按文档重做等于重写掉落核心。

## 2. 现状 vs 文档第 1 节

| 维度 | 现状 | 文档要求 |
| --- | --- | --- |
| 掉落物 | 矿石**方块**（`minecraft:iron_ore`） | **原矿物品**（`minecraft:raw_iron`） |
| 精准采集 | **硬性前置**，没有就不触发 | 取消 |
| 概率 | 每级 +8%（III 级 24%） | 0.5% / 1.0% / 2.0%，非线性 |
| 方块 | 石头、圆石、深板岩、深板岩圆石 | 石头、原石、**下界岩** |
| 矿石表 | 方块 ID，总权重 108 | 物品 ID，总权重 94 |
| 下界 | 无 | 石英 `9` : 远古残骸 `1` |
| 宝石 | 无 | LV3+ 每次 2%，6 种等权重，下界不掉 |

## 3. 已确认的决定（你定的）

1. 掉**原矿物品**，**吃时运加成**，用原版 `ore_drops` 公式，**只影响数量**。
2. 矿石池改成**「物品 ID + 权重」**，**关掉自动识别**，但**保留手动配置物品表的入口**。
3. **保留深板岩支持**（含义见 6.2）。
4. **新增 6 个自定义宝石物品**，「绿宝石」改叫**「翡翠」**。
5. 触发时**替换**原本掉落物（不是追加）。
6. 宝石骰与转化骰**完全独立**，可同时发生。
7. 主世界表 / 下界表**按方块家族**选，不按维度。

## 4. 消歧：两处「绿宝石」不是同一个东西

这是最容易写错的地方，先钉死：

- **1.2 主世界矿石表里的「绿宝石」** = `minecraft:emerald`（原版物品，挖绿宝石矿得的那个）。
- **1.4 宝石掉落里的「绿宝石」** = 自定义物品，**改名「翡翠」**，ID 为 `alchemy:jade`。

矿表掉原版 `emerald`，宝石槽掉 `alchemy:jade`，两者互不相干。

## 5. 实现落点

保留现有的 **Global Loot Modifier**（`StoneTransmutationModifier`），四步全在**同一个 `doApply`** 内完成：

1. 骰概率（`context.getRandom()`）
2. 选表并抽物品
3. 算数量（时运）
4. 替换掉落物 + 追加宝石（`generatedLoot.set` / `add`）

`data/alchemy/loot_modifiers/stone_transmutation.json` 与 `data/forge/loot_modifiers/global_loot_modifiers.json` **无需改动**。

**明确不采用**的两条路径（会坏）：
- `LivingDropsEvent`：只对生物死亡触发，拿不到方块破坏。
- `BlockEvent.BreakEvent` 里手动 spawn：会双掉落、绕过战利品表。

## 6. 方块集合

### 6.1 标签

拆成两张标签，按方块家族决定走哪张表：

`data/alchemy/tags/blocks/overworld_transmutable.json`
```json
{ "replace": false, "values": [
  "minecraft:stone", "minecraft:cobblestone",
  "minecraft:deepslate", "minecraft:cobbled_deepslate"
]}
```

`data/alchemy/tags/blocks/nether_transmutable.json`
```json
{ "replace": false, "values": [ "minecraft:netherrack" ] }
```

> 现有的 `transmutable.json` / `deepslate_transmutable.json` 删除。
> **注意**：原来漏了 `minecraft:netherrack`，不加的话下界分支永远不触发。

### 6.2 深板岩

「保留深板岩支持」在本方案下的确切含义：**深板岩 / 深板岩圆石仍算可转化方块，走主世界物品表，没有变种差异**。
因为现在掉的是物品，不存在「深板岩变种物品」。

随之**删除**：
- `ModTags.Blocks.DEEPSLATE_TRANSMUTABLE`
- `Config.resolveVariant()` 整个方法及其 `DEEPSLATE_PREFIX` 常量
- `data/alchemy/tags/blocks/deepslate_transmutable.json`

### 6.3 不转化的情形（写进测试）

- 活塞推动、爆炸破坏、命令破坏：**都不转化**（GLM 拿不到玩家工具，或根本没走玩家掉落）。
- 非镐工具：不转化（附魔本身也附不到非镐上，双保险）。

## 7. 概率表

| 等级 | 主世界转化 | 主世界宝石 | 下界转化 |
| --- | --- | --- | --- |
| 1 | 0.5% | — | 0.5% |
| 2 | 1.0% | — | 1.0% |
| 3 | 2.0% | 2.0% | 2.0% |

**校验规则**（防止配置写崩）：
- 概率表长度 < `maxLevel` 时，超出部分沿用**最后一项**并 warn。
- 每一项 clamp 到 `[0.0, 1.0]`。
- `gemChanceStartLevel > maxLevel` 时 warn 并视为「不会掉宝石」。

## 8. 掉落流程

1. 方块命中 `overworld_transmutable` 或 `nether_transmutable`，且工具上有本附魔（等级 ≥ 1）。
2. 按方块家族取对应概率与表，掷骰。**不中则跳过第 3–4 步，但仍继续第 5 步。**
3. 按权重抽一个物品（`overworldOres` 或 `netherOres`）。
4. 算数量：
   ```
   fortune = 工具上的 minecraft:fortune 等级，clamp 到 [0, 5]
   if (fortune <= 0) count = base
   else count = base * max(1, nextInt(fortune + 2))
   ```
   > **这是原版 `ore_drops` 的等价写法**：原版是 `i = max(nextInt(level+2) - 1, 0)`，倍率 `1..level+1`，III 级均值 **2.2**（不是 3.0）。
   > 我已用数值模拟核对：原版 III 级均值 2.1988。文档初稿漏了 `-1` 和 clamp，会系统性偏高 36%。
   > 石头 / 下界岩的战利品表**本身不带 `ore_drops`**（只掉 cobblestone / netherrack），所以数量必须手算。
5. 若在主世界表且等级 ≥ `gemChanceStartLevel`，**独立**掷一次宝石骰；中了追加 1 颗随机宝石（等权重，可重复）。
6. 宝石骰在转化失败时**仍然独立判定**。

**数量上限**：结果 clamp 到 64，超出拆成多堆。

## 9. 配置项

```toml
enabled = true
maxLevel = 3

overworldChancePerLevel = [0.005, 0.010, 0.020]
netherChancePerLevel    = [0.005, 0.010, 0.020]
gemChanceStartLevel = 3
gemChance = 0.020

overworldOres = [
  "minecraft:coal#30", "minecraft:raw_iron#20", "minecraft:raw_copper#15",
  "minecraft:raw_gold#10", "minecraft:redstone#8", "minecraft:lapis_lazuli#6",
  "minecraft:emerald#3", "minecraft:diamond#2"
]
netherOres = [ "minecraft:quartz#9", "minecraft:netherite_scrap#1" ]
gems = [ "alchemy:ruby", "alchemy:amber", "alchemy:topaz",
         "alchemy:jade", "alchemy:sapphire", "alchemy:amethyst" ]

fortuneAffectsCount = true
```

- **删除**：`chancePerLevel`、`transmuteAllDrops`（新方案下「最后一个掉落物」语义已失效；石头/下界岩都只掉 1 个）。
- **保留并默认关**：`autoDetectModOres`、`autoDetectWeight`、`autoDetectExclude`（你要能手动写物品表，这个入口留着；但默认 `false`）。
- `ConfigFileFormatter.formatArrays` 的 key 列表同步改为 `overworldOres` / `netherOres` / `gems` / `autoDetectExclude`。

**空表 / 全解析失败**：逐条 warn 出具体 ID，**不静默回退**。
现有 `rebuildOrePool` 里「列表全无效就回退硬编码 `DEFAULT_ORES`」的分支**必须删除**——从方块 ID 换成物品 ID 后，老配置必然全部失效，会静默命中该分支，功能看着正常、实际掉的是写死的表。

**解析时机**：配置加载时只做格式校验（validator 里不能查注册表），存 `ResourceLocation`；
在 `FMLCommonSetupEvent` 与每次 `/alchemy reload` 之后重建物品池。

## 10. 宝石物品

模组目前**没有任何物品注册**，需从零建 `ModItems`。

| ID | 中文名 | 主 buff（第 3 节用，本次只做物品） |
| --- | --- | --- |
| `alchemy:ruby` | 红宝石 | +2% 伤害增幅 |
| `alchemy:amber` | 橙宝石 | +2% 攻速 |
| `alchemy:topaz` | 黄宝石 | +1% 伤害减免 |
| `alchemy:jade` | 翡翠 | +1 幸运 |
| `alchemy:sapphire` | 蓝宝石 | +5% 移速 |
| `alchemy:amethyst` | 紫宝石 | +0.1 心 / 5s 回复 |

每个宝石需要：`DeferredRegister<Item>` 注册 + `assets/alchemy/models/item/<id>.json` + `assets/alchemy/textures/item/<id>.png` + 中英文名 + 创造模式标签页。
> `alchemy:amethyst` 与 `minecraft:amethyst_shard`（紫水晶碎片）中文不撞名，安全。

## 11. 验证

**纯数学抽函数**（对齐已有的 `DoubleStrikeMath` / `MiniProtectionMath` 风格）：
- 概率表取值与越界回退
- 权重抽取分布
- 时运数量公式（含 `fortune=0`、clamp 5、上限 64）
- 宝石是否触发

**GameTest**：用 `makeMockPlayer` + `gameMode.destroyBlock`，通过**配置覆盖把概率强制为 1.0 / 0.0**，避免 0.5% 概率导致 flaky：
- 概率 1.0 + 无时运 → 掉 1 份矿石、宝石等级不够时不掉宝石
- 概率 1.0 + 时运 → 数量落在公式区间内
- 概率 0.0 + 宝石 1.0 → 只掉宝石、不掉矿石（验证两个骰独立）
- 挖下界岩 → 走 `netherOres` 表

**RCON 端到端**：`/loot mine` 由玩家执行时会带上 `THIS_ENTITY`（`getEntity()`），所以可以 `execute as <玩家> run loot mine <pos>` 验证玩家路径。

## 12. 与后续章节的接口

第 2.3 节「镐每级强化额外 +2% Midas 触发概率」将来要叠加到本附魔的概率上。
所以概率计算留一个 `bonusChance` 参数（本次传 0），避免以后推翻重构。
