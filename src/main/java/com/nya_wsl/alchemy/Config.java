package com.nya_wsl.alchemy;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
import java.util.regex.Pattern;

/**
 * 通用配置：附魔等级上限、触发概率、可转化出的矿石方块权重表。
 * 生成的文件位于 config/alchemy-common.toml。
 * <p>
 * 矿石池 = 配置里显式写出的 {@code ores} + 自动识别到的（模组）矿石方块。
 * 池里存的是「石头类」矿石方块（如 minecraft:iron_ore），实际掉落时再按挖掘的
 * 方块类型换成深板岩变种（minecraft:deepslate_iron_ore）。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config
{
    private static final List<String> DEFAULT_ORES = List.of(
            "minecraft:coal_ore#40",
            "minecraft:copper_ore#25",
            "minecraft:iron_ore#20",
            "minecraft:redstone_ore#8",
            "minecraft:gold_ore#6",
            "minecraft:lapis_ore#5",
            "minecraft:diamond_ore#2",
            "minecraft:emerald_ore#2"
    );

    /** 自动识别模组矿石方块：{@code *_ore}，深板岩变种（{@code deepslate_*_ore}）由基础矿石推导，不单独入池。 */
    private static final Pattern ORE_BLOCK_PATTERN = Pattern.compile("^[a-z0-9_]+_ore$");

    private static final String DEEPSLATE_PREFIX = "deepslate_";

    private static final Pattern ORE_ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_LEVEL = BUILDER
            .comment("附魔最大等级。")
            .defineInRange("maxLevel", 3, 1, 10);

    private static final ForgeConfigSpec.DoubleValue CHANCE_PER_LEVEL = BUILDER
            .comment("每一级附魔把掉落物变成矿石方块的概率（0.0 ~ 1.0），需要镐上带精准采集才会触发。",
                    "实际概率 = 本值 x 附魔等级，上限为 1.0。例如 0.08 时，III 级为 24%。")
            .defineInRange("chancePerLevel", 0.08D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.BooleanValue TRANSMUTE_ALL_DROPS = BUILDER
            .comment("true：该方块的所有掉落物都被转化；false：只转化第一个掉落物。")
            .define("transmuteAllDrops", false);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ORES = BUILDER
            .comment("可转化出的矿石方块列表，格式为 \"方块ID\" 或 \"方块ID#权重\"，权重越大越常见。",
                    "示例：\"minecraft:diamond_ore#2\"。",
                    "这里只写石头类矿石（如 minecraft:iron_ore），挖深板岩时会自动换成对应变种；",
                    "这里的条目优先于自动识别结果（同 ID 以本列表的权重为准）。")
            .defineList("ores", DEFAULT_ORES, Config::validateOreEntry);

    private static final ForgeConfigSpec.BooleanValue AUTO_DETECT_MOD_ORES = BUILDER
            .comment("是否自动把已安装模组的矿石方块（*_ore）并入矿石池。",
                    "只扫描 minecraft 之外的命名空间，原版矿石请用上面的 ores 显式配置；",
                    "深板岩变种（deepslate_*_ore）不单独入池，由对应的基础矿石自动推导。")
            .define("autoDetectModOres", true);

    private static final ForgeConfigSpec.IntValue AUTO_DETECT_WEIGHT = BUILDER
            .comment("自动识别出的矿石方块使用的权重。自动条目越多，单个矿石的出现概率越低。")
            .defineInRange("autoDetectWeight", 2, 1, 1000);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_DETECT_EXCLUDE = BUILDER
            .comment("自动识别时要排除的方块 ID 关键字（包含即排除），例如 \"nether_\"、\"mymod:\"。")
            .defineListAllowEmpty("autoDetectExclude", List.of(), Config::validateExcludeKeyword);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 以下字段在配置加载 / 重载后更新，供运行期快速读取（volatile：重载可能发生在监听线程上）。 */
    public static volatile int maxLevel = 3;
    public static volatile double chancePerLevel = 0.08D;
    public static volatile boolean transmuteAllDrops = false;

    /** 配置里显式写出的原始条目，注册表就绪后再解析，避免加载顺序问题。 */
    private static List<String> explicitOreIds = DEFAULT_ORES;
    private static boolean autoDetectModOres = true;
    private static int autoDetectWeight = 2;
    private static List<String> autoDetectExclude = List.of();

    private static volatile List<OreEntry> ores = List.of();

    /** 上一次生效的配置快照，用于避免重复打日志。 */
    private static volatile String lastApplied = "";

    /** Forge 的配置对象，{@code /alchemy reload} 需要用它重新读盘。 */
    private static volatile ModConfig modConfig;

    private Config()
    {
    }

    /**
     * 按权重随机抽取一个矿石方块。
     *
     * @param deepslateOre 玩家挖的是否为深板岩类方块，是则优先换成深板岩变种
     * @return 抽取结果（方块物品），矿石池为空时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack rollOre(boolean deepslateOre, RandomSource random)
    {
        List<OreEntry> pool = ores;
        if (pool.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        int totalWeight = 0;
        for (OreEntry entry : pool)
        {
            totalWeight += entry.weight();
        }

        int roll = random.nextInt(totalWeight);
        OreEntry picked = pool.get(pool.size() - 1);
        for (OreEntry entry : pool)
        {
            roll -= entry.weight();
            if (roll < 0)
            {
                picked = entry;
                break;
            }
        }

        return createStack(resolveVariant(picked.block(), deepslateOre));
    }

    private static ItemStack createStack(Block block)
    {
        Item item = block.asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * 按命名约定推导深板岩变种：优先 {@code deepslate_<name>}，其次 {@code <name>_deepslate}，
     * 都没有就退回原方块（模组不一定提供变种）。
     */
    private static Block resolveVariant(Block base, boolean deepslateOre)
    {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(base);
        if (id == null)
        {
            return base;
        }

        String namespace = id.getNamespace();
        String path = id.getPath();

        if (deepslateOre)
        {
            if (path.startsWith(DEEPSLATE_PREFIX))
            {
                return base;
            }

            Block prefixed = ForgeRegistries.BLOCKS.getValue(
                    ResourceLocation.fromNamespaceAndPath(namespace, DEEPSLATE_PREFIX + path));
            if (prefixed != null)
            {
                return prefixed;
            }

            Block suffixed = ForgeRegistries.BLOCKS.getValue(
                    ResourceLocation.fromNamespaceAndPath(namespace, path + "_deepslate"));
            return suffixed != null ? suffixed : base;
        }

        if (path.startsWith(DEEPSLATE_PREFIX))
        {
            Block stripped = ForgeRegistries.BLOCKS.getValue(
                    ResourceLocation.fromNamespaceAndPath(namespace, path.substring(DEEPSLATE_PREFIX.length())));
            if (stripped != null)
            {
                return stripped;
            }
        }

        return base;
    }

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
        catch (Exception e)
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
        rebuildOrePool();

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
        maxLevel = MAX_LEVEL.get();
        chancePerLevel = CHANCE_PER_LEVEL.get();
        transmuteAllDrops = TRANSMUTE_ALL_DROPS.get();
        explicitOreIds = List.copyOf(ORES.get());
        autoDetectModOres = AUTO_DETECT_MOD_ORES.get();
        autoDetectWeight = AUTO_DETECT_WEIGHT.get();
        autoDetectExclude = List.copyOf(AUTO_DETECT_EXCLUDE.get());
    }

    private static String describeValues()
    {
        return "maxLevel=" + maxLevel
                + ", chancePerLevel=" + chancePerLevel
                + ", transmuteAllDrops=" + transmuteAllDrops
                + ", ores=" + ores.size()
                + ", autoDetectModOres=" + autoDetectModOres
                + ", autoDetectWeight=" + autoDetectWeight;
    }

    /** 把 Forge 生成出来的单行数组重排成每行一项，方便手动调权重。 */
    private static void reformatConfigFile()
    {
        Path file = FMLPaths.CONFIGDIR.get().resolve(ModMain.MODID + "-common.toml");
        try
        {
            if (ConfigFileFormatter.formatArrays(file, "ores", "autoDetectExclude"))
            {
                ModMain.LOGGER.debug("alchemy: 已重排配置文件中的数组：{}", file);
            }
        }
        catch (IOException e)
        {
            ModMain.LOGGER.warn("alchemy: 重排配置文件失败（不影响运行）：{}", e.toString());
        }
    }

    /**
     * 注册表在配置加载时可能还没就绪，这里再重建一次，保证模组矿石方块能被识别。
     * 同时补一次数组重排：Forge 在 Loading 事件之后还会 save() 一次，会把 Loading 时排好的数组又挤回一行。
     */
    @SubscribeEvent
    static void onCommonSetup(final FMLCommonSetupEvent event)
    {
        rebuildOrePool();
        reformatConfigFile();
    }

    private static synchronized void rebuildOrePool()
    {
        if (!ForgeRegistries.BLOCKS.iterator().hasNext())
        {
            return;
        }

        Map<ResourceLocation, OreEntry> merged = new LinkedHashMap<>();

        List<String> invalid = new ArrayList<>();
        for (String raw : explicitOreIds)
        {
            OreEntry entry = parseOreEntry(raw);
            ResourceLocation id = entry == null ? null : ForgeRegistries.BLOCKS.getKey(entry.block());
            if (id == null)
            {
                invalid.add(raw);
                continue;
            }
            merged.putIfAbsent(id, entry);
        }

        if (!invalid.isEmpty())
        {
            ModMain.LOGGER.warn("alchemy: ores 中有 {} 项无法解析为方块（应为方块 ID，如 minecraft:iron_ore）：{}",
                    invalid.size(), String.join(", ", invalid));
        }

        // 列表写了但全部无效（例如从旧版物品 ID 升级上来）时回退到默认矿石，避免功能直接失效
        if (merged.isEmpty() && !explicitOreIds.isEmpty())
        {
            for (String raw : DEFAULT_ORES)
            {
                OreEntry entry = parseOreEntry(raw);
                ResourceLocation id = entry == null ? null : ForgeRegistries.BLOCKS.getKey(entry.block());
                if (id != null)
                {
                    merged.putIfAbsent(id, entry);
                }
            }
            ModMain.LOGGER.warn("alchemy: ores 全部无效，已回退到默认矿石方块列表。");
        }

        int explicitCount = merged.size();

        List<String> detected = new ArrayList<>();
        if (autoDetectModOres)
        {
            for (Block block : ForgeRegistries.BLOCKS)
            {
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                if (id == null || merged.containsKey(id) || isExcluded(id) || !isOreLikeBlock(id))
                {
                    continue;
                }

                OreEntry entry = new OreEntry(block, autoDetectWeight);
                merged.put(id, entry);
                detected.add(formatOreEntry(id, autoDetectWeight));
            }
        }

        ores = List.copyOf(merged.values());

        if (detected.isEmpty())
        {
            ModMain.LOGGER.info("alchemy: 矿石池共 {} 项（显式配置），未自动识别到额外矿石方块。", explicitCount);
        }
        else
        {
            ModMain.LOGGER.info("alchemy: 矿石池共 {} 项，其中自动识别 {} 项。可直接粘贴到 ores 配置固定这段格式：{}",
                    ores.size(), detected.size(), String.join(", ", detected));
        }
    }

    /** 是否符合自动识别规则：模组命名空间下的 {@code *_ore}，深板岩变种不单独入池。 */
    private static boolean isOreLikeBlock(ResourceLocation id)
    {
        if ("minecraft".equals(id.getNamespace()))
        {
            return false;
        }

        String path = id.getPath();
        return !path.startsWith(DEEPSLATE_PREFIX) && ORE_BLOCK_PATTERN.matcher(path).matches();
    }

    private static boolean isExcluded(ResourceLocation id)
    {
        String fullId = id.toString();
        for (String keyword : autoDetectExclude)
        {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && fullId.contains(trimmed))
            {
                return true;
            }
        }
        return false;
    }

    /** 统一的条目格式化：{@code namespace:path#weight}。 */
    public static String formatOreEntry(ResourceLocation id, int weight)
    {
        return id + "#" + weight;
    }

    private static OreEntry parseOreEntry(String raw)
    {
        if (raw == null)
        {
            return null;
        }

        String value = raw.trim();
        int weight = 10;
        int hash = value.lastIndexOf('#');
        if (hash > 0)
        {
            try
            {
                weight = Integer.parseInt(value.substring(hash + 1).trim());
            }
            catch (NumberFormatException e)
            {
                return null;
            }
            value = value.substring(0, hash).trim();
            if (weight <= 0)
            {
                return null;
            }
        }

        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id))
        {
            return null;
        }

        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == null ? null : new OreEntry(block, weight);
    }

    /**
     * 只校验格式，不校验注册表：配置加载时注册表可能尚未填充，
     * 真正的方块解析放到 {@link #rebuildOrePool()} 里做。
     */
    private static boolean validateOreEntry(final Object obj)
    {
        if (!(obj instanceof final String name))
        {
            return false;
        }

        String value = name.trim();
        int hash = value.lastIndexOf('#');
        if (hash > 0)
        {
            try
            {
                if (Integer.parseInt(value.substring(hash + 1).trim()) <= 0)
                {
                    return false;
                }
            }
            catch (NumberFormatException e)
            {
                return false;
            }
            value = value.substring(0, hash).trim();
        }

        return ORE_ID_PATTERN.matcher(value).matches();
    }

    private static boolean validateExcludeKeyword(final Object obj)
    {
        return obj instanceof final String keyword && !keyword.trim().isEmpty();
    }

    /** 一条矿石方块权重配置。 */
    public record OreEntry(Block block, int weight)
    {
    }
}
