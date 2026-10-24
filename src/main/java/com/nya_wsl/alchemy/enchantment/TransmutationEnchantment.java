package com.nya_wsl.alchemy.enchantment;

import com.nya_wsl.alchemy.Config;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.common.ToolActions;

/**
 * 点石成金。只能附在镐上，等级越高触发概率越高（概率见 {@link Config#chancePerLevel}）。
 * 实际生效还要求镐上同时带有精准采集，具体逻辑见 loot/StoneTransmutationModifier。
 */
public class TransmutationEnchantment extends Enchantment
{
    public TransmutationEnchantment()
    {
        super(Rarity.RARE, EnchantmentCategory.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel()
    {
        return Math.max(1, Config.maxLevel);
    }

    @Override
    public int getMinCost(int level)
    {
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxCost(int level)
    {
        return getMinCost(level) + 20 + (level - 1) * 9;
    }

    @Override
    public boolean canEnchant(ItemStack stack)
    {
        // 仅镐类工具（兼容模组镐：优先看 ToolAction，其次看是否为原版 PickaxeItem 子类）
        return stack.canPerformAction(ToolActions.PICKAXE_DIG) || stack.getItem() instanceof PickaxeItem;
    }

    @Override
    public boolean isTreasureOnly()
    {
        return false;
    }

    @Override
    public boolean isDiscoverable()
    {
        return true;
    }
}
