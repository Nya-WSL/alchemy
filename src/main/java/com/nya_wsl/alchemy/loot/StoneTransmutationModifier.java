package com.nya_wsl.alchemy.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nya_wsl.alchemy.Config;
import com.nya_wsl.alchemy.ModTags;
import com.nya_wsl.alchemy.api.MidasChanceHooks;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.List;

/**
 * 全局掉落修饰器：玩家用带「点石成金」的镐挖掘可转化方块时，把掉落物换成原矿物品，并可能额外掉宝石。
 * <p>
 * 四步都在 {@link #doApply} 内完成（设计文档第 5 节）：
 * <ol>
 *   <li>骰概率（按方块家族选主世界 / 下界那张表）</li>
 *   <li>按权重抽一个物品</li>
 *   <li>算数量（时运，用原版 {@code ore_drops} 公式）</li>
 *   <li>替换掉落物 + 独立追加宝石</li>
 * </ol>
 * <p>
 * 走 GLM 而不是 {@code LivingDropsEvent} / {@code BlockEvent.BreakEvent}：前者拿不到方块破坏，
 * 后者会双掉落并绕过战利品表。
 */
public class StoneTransmutationModifier extends LootModifier
{
    public static final Codec<StoneTransmutationModifier> CODEC = RecordCodecBuilder.create(
            instance -> codecStart(instance).apply(instance, StoneTransmutationModifier::new));

    protected StoneTransmutationModifier(final LootItemCondition[] conditions)
    {
        super(conditions);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(final ObjectArrayList<ItemStack> generatedLoot,
                                                 final LootContext context)
    {
        if (!Config.enabled)
        {
            return generatedLoot;
        }

        final BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null)
        {
            return generatedLoot;
        }

        // 按方块家族决定走哪张表；两类都不属于就直接退出（活塞 / 爆炸 / 命令破坏也走不到这里，
        // 因为那时 LootContext 里没有玩家工具，下面 THIS_ENTITY 不是 Player）
        final boolean overworld = state.is(ModTags.Blocks.OVERWORLD_TRANSMUTABLE);
        final boolean nether = state.is(ModTags.Blocks.NETHER_TRANSMUTABLE);
        if (!overworld && !nether)
        {
            return generatedLoot;
        }

        final Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof Player player))
        {
            return generatedLoot;
        }

        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool == null)
        {
            tool = player.getMainHandItem();
        }

        final int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.TURN_STONE_INTO_GOLD.get(), tool);
        if (enchantLevel <= 0)
        {
            return generatedLoot;
        }

        final RandomSource random = context.getRandom();

        // ---- 第 1 步：骰概率
        final List<Double> chanceTable = overworld
                ? Config.overworldChancePerLevel
                : Config.netherChancePerLevel;
        // 倍率由外部模组注册（强化系统的「镐每级强化提升触发概率」走这里）。
        // 没有注册者时 multiplier 返回 1.0，概率与加钩子之前逐位相同。
        final double baseChance = TransmutationMath.chanceFor(enchantLevel, chanceTable, 0.0D);
        final double chance = TransmutationMath.clamp01(
                baseChance * MidasChanceHooks.multiplier(player, tool, enchantLevel));
        final boolean transmute = chance > 0.0D && random.nextDouble() < chance;

        // ---- 第 2–4 步：只在命中时替换掉落物
        if (transmute)
        {
            final List<Config.WeightedItem> pool = overworld ? Config.overworldPool() : Config.netherPool();
            final Item picked = Config.rollFromPool(pool, random);
            if (picked != null)
            {
                replaceDrops(generatedLoot, picked, tool, random);
            }
        }

        // ---- 宝石骰：与转化骰完全独立，转化失败时照掷（设计文档 3.6 / 8.6）
        rollGems(generatedLoot, overworld, enchantLevel, random);

        return generatedLoot;
    }

    /**
     * 把原有掉落物替换成抽中的矿物品。
     * <p>
     * 新方案下石头 / 下界岩都只掉 1 个，所以没有「只换第一个还是全换」的语义了——
     * 直接把整个列表换成一份（可能多堆）矿物品。原掉落物被<b>替换</b>而不是追加。
     */
    private static void replaceDrops(final ObjectArrayList<ItemStack> generatedLoot,
                                     final Item item,
                                     final ItemStack tool,
                                     final RandomSource random)
    {
        final int count = rollCount(tool, random);
        if (count <= 0)
        {
            return;
        }

        generatedLoot.clear();
        for (final int stackSize : TransmutationMath.splitIntoStacks(count))
        {
            generatedLoot.add(new ItemStack(item, stackSize));
        }
    }

    /**
     * 算掉落数量。
     * <p>
     * 用原版 {@code ore_drops} 公式，但必须自己算：石头 / 下界岩的战利品表本身不带 {@code ore_drops}
     * （它们只掉 cobblestone / netherrack），所以没有现成的函数可以复用。
     */
    private static int rollCount(final ItemStack tool, final RandomSource random)
    {
        final int base = 1;
        if (!Config.fortuneAffectsCount)
        {
            return base;
        }

        final int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        final int clamped = Math.max(0, Math.min(TransmutationMath.MAX_FORTUNE_LEVEL, fortune));
        if (clamped <= 0)
        {
            return base;
        }

        return TransmutationMath.fortuneCount(base, clamped, random.nextInt(clamped + 2));
    }

    /** 达到起点等级且概率为正时，独立掷一次宝石骰；下界不掉宝石。 */
    private static void rollGems(final ObjectArrayList<ItemStack> generatedLoot,
                                 final boolean overworld,
                                 final int enchantLevel,
                                 final RandomSource random)
    {
        // 设计文档 1.4：宝石只在主世界表掉落
        if (!overworld)
        {
            return;
        }

        final double gemChance = TransmutationMath.gemChanceFor(
                enchantLevel, Config.gemChanceStartLevel, Config.gemChance);
        if (gemChance <= 0.0D || random.nextDouble() >= gemChance)
        {
            return;
        }

        final List<Item> gems = Config.gemPool();
        if (gems.isEmpty())
        {
            return;
        }

        generatedLoot.add(new ItemStack(gems.get(random.nextInt(gems.size()))));
    }
}
