# alchemy

一个 **Minecraft 1.20.1 Forge** 模组，目前只包含一样东西：

## 点石成金（Turn Stone into Gold）

配合**精准采集**，挖掘石头 / 圆石时有概率把掉落物变成矿石方块；挖深板岩时会换成对应的深板岩变种。
概率 = `chancePerLevel` x 附魔等级（默认每级 8%，III 级 24%）。

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

## 构建

```bash
./gradlew build          # 产物：build/libs/Alchemy-<version>.jar
./gradlew runServer      # 开发环境启动服务器
./gradlew test           # 纯计算逻辑的单元测试
```

## 验证脚本

`tools/rcon.py` 是一个极简 RCON 客户端，可以对一个开了 RCON 的开发服务器做端到端冒烟验证
（服务器需在 `run/server.properties` 里配好 `enable-rcon=true`）：

```bash
python tools/rcon.py "list" "time query gametime"
```

## 相关的独立模组

战斗向附魔（**保护mini**、**双重打击**）已拆分到独立模组 `alchemy_soulforging`，
两者互不依赖，可以单独安装。
