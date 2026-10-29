package com.nya_wsl.alchemy.gametest;

import com.nya_wsl.alchemy.Config;
import com.nya_wsl.alchemy.ModMain;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import com.nya_wsl.alchemy.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 「点石成金」的 GameTest 回归测试。
 * <p>
 * <b>为什么必须把概率强制成 1.0 / 0.0</b>：默认概率是 0.5% ~ 2%，靠真随机掷中一次要跑几百次，
 * 测试必然 flaky。每个用例开头都用 {@link #forceChances} 把 {@code Config} 的运行期字段钉死，
 * 测的是「命中之后发生了什么」——概率计算本身由 {@code TransmutationMathTest} 覆盖。
 * <p>
 * <b>为什么用 {@link Block#getDrops}</b>：它内部构造的 {@code LootParams} 会带上
 * {@code THIS_ENTITY}（玩家）与 {@code TOOL}（镐），正是 GLM 需要的两个参数，
 * 而且不会真的破坏方块、不产生物品实体，测试环境干净、可重复。
 * 命令破坏 / 活塞 / 爆炸都不会带玩家，于是永远不转化——这正是设计文档 6.3 要的行为。
 * <p>
 * 用了 {@link PrefixGameTestTemplate @PrefixGameTestTemplate(false)}，否则框架会把模板名
 * 解析成 {@code alchemy:transmutationgametest.xxx}，找不到文件直接抛错。
 */
@GameTestHolder(ModMain.MODID)
@PrefixGameTestTemplate(false)
public final class TransmutationGameTest
{
    /** 每个用例都在平台的相对 y=1 层挖中间那块（模板一层地板会被整体上移 1 格）。 */
    private static final int FLOOR_Y = 1;

    /** 模板里挖的那一格。 */
    private static final BlockPos TARGET = new BlockPos(1, FLOOR_Y, 1);

    private TransmutationGameTest()
    {
    }

    /**
     * 概率 1.0、无时运：掉 1 份矿物品，且等级不够时不掉宝石。
     * <p>
     * 同时守住两件事：掉落物确实是<b>原矿物品</b>（不是圆石，也不是矿石方块），
     * 以及 LV1 不该触发宝石槽。
     */
    @GameTest(template = "transmutation_arena", timeoutTicks = 100)
    public static void guaranteedTransmuteDropsOneOre(final GameTestHelper helper)
    {
        try (ConfigGuard guard = forceChances(1.0D, 1.0D, 3, 0.0D))
        {
            final Player player = armedMiner(helper, 1, false);
            final List<ItemStack> drops = breakBlock(helper, player, Blocks.STONE);

            helper.assertTrue(!drops.isEmpty(), "概率 1.0 却没有产生任何掉落物");

            final int oreTotal = drops.stream()
                    .filter(TransmutationGameTest::isOverworldOre)
                    .mapToInt(ItemStack::getCount)
                    .sum();
            helper.assertTrue(oreTotal == 1,
                    "无时运时应当正好掉 1 份矿物品，实际 " + oreTotal + "：" + drops);

            // 关键：原掉落物被「替换」而不是「追加」——列表里不应再有圆石
            helper.assertTrue(drops.stream().noneMatch(stack -> stack.is(Items.COBBLESTONE)),
                    "原掉落物（圆石）没有被替换掉：" + drops);

            helper.assertTrue(drops.stream().noneMatch(TransmutationGameTest::isGem),
                    "LV1 不该掉落宝石，实际掉了：" + drops);

            helper.succeed();
        }
    }

    /**
     * 概率 1.0 + 时运 III：数量会出现大于 1，且不超过原版公式上界 4。
     * <p>
     * 断言的是<b>区间</b>而不是具体值，因为 {@code context.getRandom()} 不可控。
     * 连挖 30 次取最大值：只要实现退化成「忽略时运恒为 1」，{@code maxCount > 1} 就会失败。
     * 30 次里一次都抽不到 >1 的概率极低（每次至少 3/5 概率 >1）。
     */
    @GameTest(template = "transmutation_arena", timeoutTicks = 200)
    public static void fortuneIncreasesCount(final GameTestHelper helper)
    {
        try (ConfigGuard guard = forceChances(1.0D, 1.0D, 3, 0.0D))
        {
            final Player player = armedMiner(helper, 3, true);

            int maxCount = 0;
            for (int attempt = 0; attempt < 30; attempt++)
            {
                final List<ItemStack> drops = breakBlock(helper, player, Blocks.STONE);
                final int total = drops.stream()
                        .filter(TransmutationGameTest::isOverworldOre)
                        .mapToInt(ItemStack::getCount)
                        .sum();
                maxCount = Math.max(maxCount, total);
            }

            helper.assertTrue(maxCount >= 1, "30 次挖掘都没有掉出矿物品");
            helper.assertTrue(maxCount > 1,
                    "时运 III 连挖 30 次都没出现过 >1 的数量，时运没有生效（最大 " + maxCount + "）");
            // 原版公式倍率上界是 level+1，III 级最多 4
            helper.assertTrue(maxCount <= 4,
                    "数量超过了原版 ore_drops 的上界 4（实际 " + maxCount + "）");

            helper.succeed();
        }
    }

    /**
     * 概率 0.0、宝石概率 1.0：只掉宝石、不掉矿物品 —— 验证两个骰子互相独立。
     * <p>
     * 这是设计文档 3.6 / 8.6 的核心承诺。若实现把宝石塞在「转化成功」分支内部，
     * 这条用例就会拿不到宝石而失败。
     */
    @GameTest(template = "transmutation_arena", timeoutTicks = 100)
    public static void gemRollIsIndependentOfOreRoll(final GameTestHelper helper)
    {
        try (ConfigGuard guard = forceChances(0.0D, 0.0D, 1, 1.0D))
        {
            final Player player = armedMiner(helper, 1, false);
            final List<ItemStack> drops = breakBlock(helper, player, Blocks.STONE);

            helper.assertTrue(drops.stream().anyMatch(TransmutationGameTest::isGem),
                    "宝石概率 1.0 却没有掉宝石，说明宝石骰依赖了转化成功：" + drops);
            helper.assertTrue(drops.stream().noneMatch(TransmutationGameTest::isOverworldOre),
                    "转化概率 0.0 却掉出了矿物品：" + drops);

            helper.succeed();
        }
    }

    /**
     * 挖下界岩走 {@code netherOres} 表，且不掉宝石。
     * <p>
     * 守着「加了 netherrack 但漏配标签」这个坑——不配的话下界分支永远不触发。
     * 宝石概率故意拉到 1.0，用来证明「下界不掉宝石」是硬规则而不是运气。
     */
    @GameTest(template = "transmutation_arena", timeoutTicks = 100)
    public static void netherrackUsesNetherTable(final GameTestHelper helper)
    {
        try (ConfigGuard guard = forceChances(1.0D, 1.0D, 1, 1.0D))
        {
            final Player player = armedMiner(helper, 3, false);
            final List<ItemStack> drops = breakBlock(helper, player, Blocks.NETHERRACK);

            helper.assertTrue(!drops.isEmpty(), "概率 1.0 挖下界岩却没有掉落");

            helper.assertTrue(drops.stream().allMatch(TransmutationGameTest::isNetherOre),
                    "下界岩应当只掉下界物品表里的东西（石英 / 远古残骸），实际掉了：" + drops);
            helper.assertTrue(drops.stream().noneMatch(TransmutationGameTest::isGem),
                    "下界不该掉落宝石（宝石概率已拉到 1.0），实际掉了：" + drops);

            helper.succeed();
        }
    }

    /**
     * 关掉 {@code enabled} 后什么都不变：挖石头仍然是圆石。
     * <p>
     * 守的是「配置开关接错分支」这类回归。概率拉到 1.0，确保不是因为没掷中才通过。
     */
    @GameTest(template = "transmutation_arena", timeoutTicks = 100)
    public static void disabledConfigSkipsEverything(final GameTestHelper helper)
    {
        try (ConfigGuard guard = forceChances(1.0D, 1.0D, 1, 1.0D))
        {
            Config.enabled = false;

            final Player player = armedMiner(helper, 3, false);
            final List<ItemStack> drops = breakBlock(helper, player, Blocks.STONE);

            helper.assertTrue(drops.stream().noneMatch(TransmutationGameTest::isOverworldOre),
                    "enabled=false 却仍然转化出了矿物品：" + drops);
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.COBBLESTONE)),
                    "enabled=false 时挖石头应当照常掉圆石，实际：" + drops);

            helper.succeed();
        }
    }

    /**
     * 深板岩也走主世界表（设计文档 6.2：保留深板岩支持，但没有变种差异）。
     */
    @GameTest(template = "transmutation_arena", timeoutTicks = 100)
    public static void deepslateUsesOverworldTable(final GameTestHelper helper)
    {
        try (ConfigGuard guard = forceChances(1.0D, 1.0D, 3, 0.0D))
        {
            final Player player = armedMiner(helper, 3, false);
            final List<ItemStack> drops = breakBlock(helper, player, Blocks.DEEPSLATE);

            helper.assertTrue(drops.stream().anyMatch(TransmutationGameTest::isOverworldOre),
                    "深板岩应当走主世界物品表，实际掉了：" + drops);

            helper.succeed();
        }
    }

    // ---------------------------------------------------------------- 辅助

    /**
     * 把概率钉死，退出时还原。
     * <p>
     * 用 try-with-resources 而不是手写 finally：{@code helper.succeed()} 之后仍有代码要跑，
     * 少写一个还原点就会把脏配置漏给后面的用例（GameTest 在同一次服务器启动里顺序执行）。
     */
    private static ConfigGuard forceChances(final double overworld,
                                            final double nether,
                                            final int gemStartLevel,
                                            final double gemChance)
    {
        return new ConfigGuard(overworld, nether, gemStartLevel, gemChance);
    }

    /** 配置快照，close 时还原。 */
    private static final class ConfigGuard implements AutoCloseable
    {
        private final boolean enabled;
        private final List<Double> overworld;
        private final List<Double> nether;
        private final int gemStart;
        private final double gemChance;

        private ConfigGuard(final double overworld,
                            final double nether,
                            final int gemStartLevel,
                            final double gemChance)
        {
            this.enabled = Config.enabled;
            this.overworld = Config.overworldChancePerLevel;
            this.nether = Config.netherChancePerLevel;
            this.gemStart = Config.gemChanceStartLevel;
            this.gemChance = Config.gemChance;

            Config.enabled = true;
            // 表长 3，覆盖 LV1~LV3；测试里用的等级都不超过 3
            Config.overworldChancePerLevel = List.of(overworld, overworld, overworld);
            Config.netherChancePerLevel = List.of(nether, nether, nether);
            Config.gemChanceStartLevel = gemStartLevel;
            Config.gemChance = gemChance;
        }

        @Override
        public void close()
        {
            Config.enabled = this.enabled;
            Config.overworldChancePerLevel = this.overworld;
            Config.netherChancePerLevel = this.nether;
            Config.gemChanceStartLevel = this.gemStart;
            Config.gemChance = this.gemChance;
        }
    }

    /** 是否是本模组 6 种宝石之一。 */
    private static boolean isGem(final ItemStack stack)
    {
        final Item item = stack.getItem();
        return item == ModItems.RUBY.get()
                || item == ModItems.AMBER.get()
                || item == ModItems.TOPAZ.get()
                || item == ModItems.JADE.get()
                || item == ModItems.SAPPHIRE.get()
                || item == ModItems.AMETHYST.get();
    }

    /** 是否是主世界矿物品表里的物品。 */
    private static boolean isOverworldOre(final ItemStack stack)
    {
        return stack.is(Items.COAL) || stack.is(Items.RAW_IRON) || stack.is(Items.RAW_COPPER)
                || stack.is(Items.RAW_GOLD) || stack.is(Items.REDSTONE)
                || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.EMERALD) || stack.is(Items.DIAMOND);
    }

    /** 是否是下界矿物品表里的物品。 */
    private static boolean isNetherOre(final ItemStack stack)
    {
        return stack.is(Items.QUARTZ) || stack.is(Items.NETHERITE_SCRAP);
    }

    /**
     * 造一个拿镐的假玩家。
     * <p>
     * 用 {@code makeMockSurvivalPlayer()}：它会带一个真实的 {@code Player} 身份，
     * 这里并不真的去挖，只是借它让战利品表带上 {@code THIS_ENTITY}。
     */
    private static Player armedMiner(final GameTestHelper helper, final int level, final boolean fortune)
    {
        final ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(ModEnchantments.TURN_STONE_INTO_GOLD.get(), level);
        if (fortune)
        {
            pickaxe.enchant(Enchantments.BLOCK_FORTUNE, 3);
        }

        final Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        return player;
    }

    /**
     * 在指定方块上跑一次掉落计算，返回 GLM 处理后的掉落物。
     * <p>
     * 每次先重置方块状态，保证多次调用之间互不影响。
     */
    private static List<ItemStack> breakBlock(final GameTestHelper helper,
                                              final Player player,
                                              final Block block)
    {
        final ServerLevel level = helper.getLevel();
        final BlockPos absolute = helper.absolutePos(TARGET);
        level.setBlockAndUpdate(absolute, block.defaultBlockState());

        final BlockState state = level.getBlockState(absolute);
        return Block.getDrops(state, level, absolute, null, player, player.getMainHandItem());
    }
}
