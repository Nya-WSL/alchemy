package com.nya_wsl.alchemy.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nya_wsl.alchemy.Config;
import com.nya_wsl.alchemy.ModTags;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.Map;

/**
 * 全局掉落修饰器：玩家用同时带有「点石成金」与「精准采集」的镐挖掘 alchemy:transmutable 标签内的方块时，
 * 按概率把掉落物替换为随机矿石方块；挖深板岩家族方块时会换成对应的深板岩变种。
 */
public class StoneTransmutationModifier extends LootModifier
{
    public static final Codec<StoneTransmutationModifier> CODEC = RecordCodecBuilder.create(
            instance -> codecStart(instance).apply(instance, StoneTransmutationModifier::new));

    protected StoneTransmutationModifier(LootItemCondition[] conditions)
    {
        super(conditions);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null || !state.is(ModTags.Blocks.TRANSMUTABLE) || generatedLoot.isEmpty())
        {
            return generatedLoot;
        }

        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof Player player))
        {
            return generatedLoot;
        }

        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool == null)
        {
            tool = player.getMainHandItem();
        }

        Map<Enchantment, Integer> toolEnchantments = EnchantmentHelper.getEnchantments(tool);
        int enchantLevel = toolEnchantments.getOrDefault(ModEnchantments.TURN_STONE_INTO_GOLD.get(), 0);

        // 必须带精准采集才生效（掉落物本身就是方块形态）
        if (enchantLevel <= 0 || !toolEnchantments.containsKey(Enchantments.SILK_TOUCH))
        {
            return generatedLoot;
        }

        double chance = Math.min(1.0D, Config.chancePerLevel * enchantLevel);
        if (player.getRandom().nextDouble() >= chance)
        {
            return generatedLoot;
        }

        boolean deepslateOre = state.is(ModTags.Blocks.DEEPSLATE_TRANSMUTABLE);
        ItemStack rolled = Config.rollOre(deepslateOre, player.getRandom());
        if (rolled.isEmpty())
        {
            return generatedLoot;
        }

        for (int i = 0; i < generatedLoot.size(); i++)
        {
            ItemStack drop = generatedLoot.get(i);
            if (drop.isEmpty())
            {
                continue;
            }

            generatedLoot.set(i, rolled.copyWithCount(drop.getCount()));

            if (!Config.transmuteAllDrops)
            {
                break;
            }
        }

        return generatedLoot;
    }
}
