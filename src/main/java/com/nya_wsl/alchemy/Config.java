package com.nya_wsl.alchemy;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.nya_wsl.alchemy.loot.ItemPoolParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用配置：附魔等级上限、主世界/下界两张概率表、两张物品权重表、宝石设置。
 * 生成的文件位于 {@code config/alchemy-common.toml}。
 * <p>
 * 掉落的是<b>原矿物品</b>（{@code minecraft:raw_iron}）而不是矿石方块，所以物品池里存的是
 * {@link Item}。解析分两步：配置加载时只校验格式并存 {@link ResourceLocation}，
 * 注册表就绪后（{@link FMLCommonSetupEvent} 与每次 {@code /alchemy reload}）再重建物品池。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config
{
    /**
     * 默认主世界物品表，总权重 94（设计文档 1.2 节）。
     * <p>
     * 注意 {@code minecraft:emerald} 是原版物品（挖绿宝石矿得的那个），
     * 与宝石槽里的自定义「翡翠」{@code alchemy:jade} 是两回事。
     */
    private static final List<String> DEFAULT_OVERWORLD_ORES = List.of(
            "minecraft:coal#30",
            "minecraft:raw_iron#20",
            "minecraft:raw_copper#15",
            "minecraft:raw_gold#10",
            "minecraft:redstone#8",
            "minecraft:lapis_lazuli#6",
            "minecraft:emerald#3",
            "minecraft:diamond#2"
    );

    /** 默认下界物品表，总权重 10（设计文档 1.3 节：石英 9 : 远古残骸 1）。 */
    private static final List<String> DEFAULT_NETHER_ORES = List.of(
            "minecraft:quartz#9",
            "minecraft:netherite_scrap#1"
    );

    /** 6 种自定义宝石，等权重，可重复。 */
    private static final List<String> DEFAULT_GEMS = List.of(
            "alchemy:ruby",
            "alchemy:amber",
            "alchemy:topaz",
            "alchemy:jade",
            "alchemy:sapphire",
            "alchemy:amethyst"
    );

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("是否启用点石成金。关掉后附魔仍然存在，但不会触发任何掉落变化。")
            .define("enabled", true);

    private static final ForgeConfigSpec.IntValue MAX_LEVEL = BUILDER
            .comment("附魔最大等级。概率表长度可以与之不同，超出表长的等级沿用表里最后一项。")
            .defineInRange("maxLevel", 3, 1, 10);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> OVERWORLD_CHANCE_PER_LEVEL = BUILDER
            .comment("主世界方块（石头 / 圆石 / 深板岩 / 深板岩圆石）每一级的触发概率。",
                    "第 i 项对应 i+1 级：默认 [0.005, 0.010, 0.020] 即 0.5% / 1.0% / 2.0%。",
                    "这是非线性阶梯，不是「每级固定加成」。每一项会被夹到 0.0 ~ 1.0。")
            .defineList("overworldChancePerLevel",
                    List.of(0.005D, 0.010D, 0.020D),
                    Config::validateChanceEntry);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> NETHER_CHANCE_PER_LEVEL = BUILDER
            .comment("下界方块（下界岩）每一级的触发概率，格式同上。")
            .defineList("netherChancePerLevel",
                    List.of(0.005D, 0.010D, 0.020D),
                    Config::validateChanceEntry);

    private static final ForgeConfigSpec.IntValue GEM_CHANCE_START_LEVEL = BUILDER
            .comment("从第几级开始有机会掉宝石。设为大于 maxLevel 的值即视为永不掉宝石。")
            .defineInRange("gemChanceStartLevel", 3, 1, 255);

    private static final ForgeConfigSpec.DoubleValue GEM_CHANCE = BUILDER
            .comment("达到起点等级后，每次成功转化时<b>独立</b>掷一次宝石骰的概率。",
                    "宝石骰与转化骰互不影响：转化失败时宝石骰照掷。默认 0.02 即 2%。")
            .defineInRange("gemChance", 0.02D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERWORLD_ORES = BUILDER
            .comment("主世界可转化出的物品表，格式 \"物品ID\" 或 \"物品ID#权重\"，权重越大越常见。",
                    "这里写的是<b>原矿物品</b>（minecraft:raw_iron），不是矿石方块（minecraft:iron_ore）。",
                    "默认总权重 94。")
            .defineList("overworldOres", DEFAULT_OVERWORLD_ORES, Config::validateWeightedEntry);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> NETHER_ORES = BUILDER
            .comment("下界可转化出的物品表，格式同上。默认总权重 10（石英 9 : 远古残骸 1）。")
            .defineList("netherOres", DEFAULT_NETHER_ORES, Config::validateWeightedEntry);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEMS = BUILDER
            .comment("可掉落的宝石物品 ID 列表（不带权重，等概率，可重复）。",
                    "注意这里的 alchemy:jade 是自定义「翡翠」，与主世界矿表里的 minecraft:emerald 无关。")
            .defineList("gems", DEFAULT_GEMS, Config::validateItemId);

    private static final ForgeConfigSpec.BooleanValue FORTUNE_AFFECTS_COUNT = BUILDER
            .comment("转化出的物品数量是否吃时运加成。",
                    "true：用原版 ore_drops 公式，时运 III 的均值约为 2.2 倍。")
            .define("fortuneAffectsCount", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_DETECT_MOD_ORES = BUILDER
            .comment("是否自动把已安装模组的矿石物品并入主世界物品表。默认关闭。",
                    "本选项保留是为了给「不想手写物品表」留一个入口；",
                    "开启后会扫描模组命名空间下的 *_ore 方块并尝试推导同名原矿物品，结果可能不准，",
                    "建议只把它生成的内容粘贴到 overworldOres 里固定下来，然后关掉本项。")
            .define("autoDetectModOres", false);

    private static final ForgeConfigSpec.IntValue AUTO_DETECT_WEIGHT = BUILDER
            .comment("自动识别出的物品使用的权重。")
            .defineInRange("autoDetectWeight", 2, 1, 1000);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_DETECT_EXCLUDE = BUILDER
            .comment("自动识别时要排除的 ID 关键字（包含即排除），例如 \"nether_\"。")
            .defineListAllowEmpty("autoDetectExclude", List.of(), Config::validateExcludeKeyword);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 以下字段在配置加载 / 重载后更新，供运行期快速读取（volatile：重载可能发生在监听线程上）。 */
    public static volatile boolean enabled = true;
    public static volatile int maxLevel = 3;
    public static volatile List<Double> overworldChancePerLevel = List.of(0.005D, 0.010D, 0.020D);
    public static volatile List<Double> netherChancePerLevel = List.of(0.005D, 0.010D, 0.020D);
    public static volatile int gemChanceStartLevel = 3;
    public static volatile double gemChance = 0.02D;
    public static volatile boolean fortuneAffectsCount = true;

    /** 配置里显式写出的原始条目，注册表就绪后再解析。 */
    private static List<String> explicitOverworldOres = DEFAULT_OVERWORLD_ORES;
    private static List<String> explicitNetherOres = DEFAULT_NETHER_ORES;
    private static List<String> explicitGems = DEFAULT_GEMS;
    private static boolean autoDetectModOres = false;
    private static int autoDetectWeight = 2;
    private static List<String> autoDetectExclude = List.of();

    /** 解析完成的两张物品池。 */
    private static volatile List<WeightedItem> overworldPool = List.of();
    private static volatile List<WeightedItem> netherPool = List.of();
    private static volatile List<Item> gemPool = List.of();

    /** 上一次生效的配置快照，用于避免重复打日志。 */
    private static volatile String lastApplied = "";

    /** Forge 的配置对象，{@code /alchemy reload} 需要用它重新读盘。 */
    private static volatile ModConfig modConfig;

    private Config()
    {
    }

    // ---------------------------------------------------------------- 运行期查询

    /**
     * 按权重从指定物品池抽一个物品。
     *
     * @param pool   物品池
     * @param random 随机源
     * @return 抽中的物品；池为空或权重异常时返回 {@code null}
     */
    public static Item rollFromPool(final List<WeightedItem> pool, final net.minecraft.util.RandomSource random)
    {
        if (pool == null || pool.isEmpty())
        {
            return null;
        }

        final List<Integer> weights = new ArrayList<>(pool.size());
        for (final WeightedItem entry : pool)
        {
            weights.add(entry.weight());
        }

        final int total = com.nya_wsl.alchemy.loot.TransmutationMath.totalWeight(weights);
        if (total <= 0)
        {
            return null;
        }

        final int index = com.nya_wsl.alchemy.loot.TransmutationMath.weightedIndex(weights, random.nextInt(total));
        return index < 0 ? null : pool.get(index).item();
    }

    /** 主世界物品池（只读快照）。 */
    public static List<WeightedItem> overworldPool()
    {
        return overworldPool;
    }

    /** 下界物品池（只读快照）。 */
    public static List<WeightedItem> netherPool()
    {
        return netherPool;
    }

    /** 宝石池（只读快照）。 */
    public static List<Item> gemPool()
    {
        return gemPool;
    }

    // ---------------------------------------------------------------- 配置事件

    @SubscribeEvent
    static void onLoading(final ModConfigEvent.Loading event)
    {
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }

        modConfig = event.getConfig();
        applyAndReport(false);
    }

    /**
     * Forge 的文件监听发现配置变化时会触发本事件（只有原地保存才会触发，编辑器原子保存收不到）。
     * 这里刻意不自动应用：改完配置后执行 {@code /alchemy reload} 才生效。
     */
    @SubscribeEvent
    static void onReloading(final ModConfigEvent.Reloading event)
    {
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }

        modConfig = event.getConfig();
        ModMain.LOGGER.info("alchemy: 检测到配置文件变化，执行 /alchemy reload 使其生效。");
    }

    /**
     * 注册表在配置加载时可能还没就绪，这里再重建一次物品池。
     * 同时补一次数组重排：Forge 在 Loading 事件之后还会 save() 一次，会把排好的数组又挤回一行。
     */
    @SubscribeEvent
    static void onCommonSetup(final FMLCommonSetupEvent event)
    {
        rebuildItemPools();
        reformatConfigFile();
    }

    /**
     * {@code /alchemy reload}：把配置文件重新读进 Forge 持有的 config 对象
     * （{@code ConfigValue#get()} 读的就是它），再刷新运行期参数。
     *
     * @return 是否重载成功（文件解析失败时返回 false，继续沿用当前配置）
     */
    static boolean reloadFromDisk()
    {
        final ModConfig config = modConfig;
        if (config == null)
        {
            return false;
        }

        final CommentedConfig data = config.getConfigData();
        if (!(data instanceof final CommentedFileConfig fileConfig))
        {
            return false;
        }

        try
        {
            fileConfig.load();
        }
        catch (final Exception e)
        {
            ModMain.LOGGER.warn("alchemy: 重新读取配置文件失败，继续使用当前配置：{}", e.toString());
            return false;
        }

        // 清掉 ForgeConfigSpec 里的取值缓存，否则 get() 还是旧值
        SPEC.afterReload();

        if (!SPEC.isCorrect(fileConfig))
        {
            ModMain.LOGGER.warn("alchemy: 配置文件里有非法条目，已按默认值修正。");
            SPEC.correct(fileConfig);
            fileConfig.save();
        }

        reformatConfigFile();
        applyAndReport(true);
        return true;
    }

    /** 应用配置并输出结果；内容没变时只打 debug 日志，避免刷屏。 */
    private static void applyAndReport(final boolean reload)
    {
        applyValues();
        rebuildItemPools();
        warnIfMisconfigured();

        final String current = describeValues();
        if (reload && current.equals(lastApplied))
        {
            ModMain.LOGGER.debug("alchemy: 配置重载后无变化（{}）。", current);
            return;
        }

        lastApplied = current;
        ModMain.LOGGER.info("alchemy: 配置已{}（{}）。", reload ? "重载" : "加载", current);
    }

    /** 当前生效的配置摘要，供命令反馈使用。 */
    static String currentValues()
    {
        return describeValues();
    }

    /** 把配置文件里的值同步到运行期字段。 */
    private static void applyValues()
    {
        enabled = ENABLED.get();
        maxLevel = MAX_LEVEL.get();
        overworldChancePerLevel = List.copyOf(OVERWORLD_CHANCE_PER_LEVEL.get());
        netherChancePerLevel = List.copyOf(NETHER_CHANCE_PER_LEVEL.get());
        gemChanceStartLevel = GEM_CHANCE_START_LEVEL.get();
        gemChance = GEM_CHANCE.get();
        fortuneAffectsCount = FORTUNE_AFFECTS_COUNT.get();
        explicitOverworldOres = List.copyOf(OVERWORLD_ORES.get());
        explicitNetherOres = List.copyOf(NETHER_ORES.get());
        explicitGems = List.copyOf(GEMS.get());
        autoDetectModOres = AUTO_DETECT_MOD_ORES.get();
        autoDetectWeight = AUTO_DETECT_WEIGHT.get();
        autoDetectExclude = List.copyOf(AUTO_DETECT_EXCLUDE.get());
    }

    /** 概率表长度 / 宝石起点这类"能跑但显然不对"的配置，给一条明确的 warn。 */
    private static void warnIfMisconfigured()
    {
        warnChanceTable("overworldChancePerLevel", overworldChancePerLevel);
        warnChanceTable("netherChancePerLevel", netherChancePerLevel);

        if (gemChanceStartLevel > maxLevel)
        {
            ModMain.LOGGER.warn("alchemy: gemChanceStartLevel={} 大于 maxLevel={}，本模组的宝石永远不会掉落。",
                    gemChanceStartLevel, maxLevel);
        }

        if (!enabled)
        {
            ModMain.LOGGER.info("alchemy: enabled=false，点石成金不会触发。");
        }
    }

    private static void warnChanceTable(final String key, final List<Double> table)
    {
        if (table.size() < maxLevel)
        {
            ModMain.LOGGER.warn("alchemy: {} 只有 {} 项，小于 maxLevel={}；"
                            + "等级 {} 及以上会沿用最后一项 {}。",
                    key, table.size(), maxLevel, table.size() + 1,
                    table.isEmpty() ? "（空表，永不触发）" : table.get(table.size() - 1));
        }
    }

    private static String describeValues()
    {
        return "enabled=" + enabled
                + ", maxLevel=" + maxLevel
                + ", overworldChancePerLevel=" + overworldChancePerLevel
                + ", netherChancePerLevel=" + netherChancePerLevel
                + ", gemChanceStartLevel=" + gemChanceStartLevel
                + ", gemChance=" + gemChance
                + ", fortuneAffectsCount=" + fortuneAffectsCount
                + ", overworldOres=" + overworldPool.size()
                + ", netherOres=" + netherPool.size()
                + ", gems=" + gemPool.size()
                + ", autoDetectModOres=" + autoDetectModOres;
    }

    /** 把 Forge 生成出来的单行数组重排成每行一项，方便手动调权重。 */
    private static void reformatConfigFile()
    {
        final Path file = FMLPaths.CONFIGDIR.get().resolve(ModMain.MODID + "-common.toml");
        try
        {
            if (ConfigFileFormatter.formatArrays(file,
                    "overworldOres", "netherOres", "gems", "autoDetectExclude"))
            {
                ModMain.LOGGER.debug("alchemy: 已重排配置文件中的数组：{}", file);
            }
        }
        catch (final IOException e)
        {
            ModMain.LOGGER.warn("alchemy: 重排配置文件失败（不影响运行）：{}", e.toString());
        }
    }

    // ---------------------------------------------------------------- 物品池重建

    private static synchronized void rebuildItemPools()
    {
        if (!ForgeRegistries.ITEMS.iterator().hasNext())
        {
            // 注册表还没就绪，等 FMLCommonSetupEvent 再来一次
            return;
        }

        overworldPool = List.copyOf(buildWeightedPool(
                "overworldOres", explicitOverworldOres, true));
        netherPool = List.copyOf(buildWeightedPool(
                "netherOres", explicitNetherOres, false));
        gemPool = List.copyOf(buildGemPool());
    }

    /**
     * 解析一张带权重的物品表。
     * <p>
     * 刻意<b>没有</b>「全部无效就回退默认表」的分支：从方块 ID 换成物品 ID 后，
     * 老配置必然全部失效，若有回退，功能会看着正常、实际掉的是写死的表。
     * 设计文档第 9 节明确要求删除该分支，坏条目逐条 warn。
     *
     * @param key            配置键名，用于日志
     * @param entries        原始条目
     * @param allowAutoDetect 是否允许把自动识别的模组矿石并进来
     */
    private static List<WeightedItem> buildWeightedPool(
            final String key,
            final List<String> entries,
            final boolean allowAutoDetect)
    {
        final Map<ResourceLocation, WeightedItem> merged = new LinkedHashMap<>();
        final List<String> invalid = new ArrayList<>();

        for (final String raw : entries)
        {
            final ItemPoolParser.Entry parsed = ItemPoolParser.parseWeighted(raw);
            if (parsed == null)
            {
                invalid.add(raw);
                continue;
            }

            final Item item = ForgeRegistries.ITEMS.getValue(parsed.id());
            if (item == null)
            {
                invalid.add(raw);
                continue;
            }

            merged.putIfAbsent(parsed.id(), new WeightedItem(item, parsed.weight()));
        }

        if (!invalid.isEmpty())
        {
            ModMain.LOGGER.warn("alchemy: {} 中有 {} 项无法解析为物品（应为物品 ID，如 minecraft:raw_iron）：{}",
                    key, invalid.size(), String.join(", ", invalid));
        }

        if (merged.isEmpty())
        {
            ModMain.LOGGER.warn("alchemy: {} 解析后没有任何有效物品，该表对应的方块将不会发生转化。", key);
        }

        if (allowAutoDetect && autoDetectModOres)
        {
            merged.putAll(detectModOres(merged));
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * 自动识别模组矿石，供不想手写物品表的用户使用（默认关闭）。
     * <p>
     * 只扫描非 minecraft 命名空间下的 {@code *_ore} 方块，按「方块 ID 去掉 _ore 再加 raw_」猜原矿物品，
     * 猜不到就跳过。因为这只是启发式，日志里会把生成结果打出来供粘贴固定。
     */
    private static Map<ResourceLocation, WeightedItem> detectModOres(final Map<ResourceLocation, WeightedItem> existing)
    {
        final Map<ResourceLocation, WeightedItem> detected = new LinkedHashMap<>();
        final List<String> detectedText = new ArrayList<>();

        for (final Item item : ForgeRegistries.ITEMS)
        {
            final ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || "minecraft".equals(id.getNamespace()) || isExcluded(id))
            {
                continue;
            }

            final String path = id.getPath();
            if (!path.startsWith("raw_") || existing.containsKey(id))
            {
                continue;
            }

            detected.put(id, new WeightedItem(item, autoDetectWeight));
            detectedText.add(id + "#" + autoDetectWeight);
        }

        if (detectedText.isEmpty())
        {
            ModMain.LOGGER.info("alchemy: autoDetectModOres 已开启，但没识别到额外的原矿物品。");
        }
        else
        {
            ModMain.LOGGER.info("alchemy: autoDetectModOres 识别到 {} 项，可粘贴到 overworldOres 固定：{}",
                    detectedText.size(), String.join(", ", detectedText));
        }

        return detected;
    }

    private static List<Item> buildGemPool()
    {
        final List<Item> gems = new ArrayList<>();
        final List<String> invalid = new ArrayList<>();

        for (final String raw : explicitGems)
        {
            final ResourceLocation id = ItemPoolParser.parseId(raw);
            final Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null)
            {
                invalid.add(raw);
                continue;
            }

            gems.add(item);
        }

        if (!invalid.isEmpty())
        {
            ModMain.LOGGER.warn("alchemy: gems 中有 {} 项无法解析为物品：{}",
                    invalid.size(), String.join(", ", invalid));
        }

        if (gems.isEmpty())
        {
            ModMain.LOGGER.warn("alchemy: gems 解析后为空，不会掉落任何宝石。");
        }

        return gems;
    }

    private static boolean isExcluded(final ResourceLocation id)
    {
        final String fullId = id.toString();
        for (final String keyword : autoDetectExclude)
        {
            final String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && fullId.contains(trimmed))
            {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------- 校验器

    /** 只校验格式，不查注册表：配置加载时注册表可能尚未填充。 */
    private static boolean validateWeightedEntry(final Object obj)
    {
        return obj instanceof final String text && ItemPoolParser.parseWeighted(text) != null;
    }

    private static boolean validateItemId(final Object obj)
    {
        return obj instanceof final String text && ItemPoolParser.parseId(text) != null;
    }

    private static boolean validateChanceEntry(final Object obj)
    {
        return obj instanceof final Double chance && !chance.isNaN() && !chance.isInfinite();
    }

    private static boolean validateExcludeKeyword(final Object obj)
    {
        return obj instanceof final String keyword && !keyword.trim().isEmpty();
    }

    /** 一条物品权重配置（运行期已解析成物品）。 */
    public record WeightedItem(Item item, int weight)
    {
        /** 便于日志与调试输出。 */
        public ItemStack createStack()
        {
            return new ItemStack(item);
        }
    }
}
