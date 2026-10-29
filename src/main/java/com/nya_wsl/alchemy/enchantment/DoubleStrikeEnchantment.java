package com.nya_wsl.alchemy.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 双重打击：用剑命中后，等无敌帧走完再补一次相当于原伤害 50% 的伤害，代价是多消耗一倍耐久。
 * <p>
 * 只在剑上出现：{@link EnchantmentCategory#WEAPON} 的判定条件就是
 * {@code item instanceof SwordItem}，因此斧、三叉戟等武器都不会获得本附魔，
 * 也不需要在 {@link Enchantment#canEnchant} 里额外限制。
 * <p>
 * 具体结算见 combat/DoubleStrikeHandler：第二段用的是模组自己的伤害类型
 * {@code alchemy:double_strike}，它被登记进 {@code minecraft:bypasses_cooldown} 标签，
 * 所以即使受击者还在无敌帧里也能命中。
 */
public class DoubleStrikeEnchantment extends Enchantment
{
    public DoubleStrikeEnchantment()
    {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{
                EquipmentSlot.MAINHAND
        });
    }

    /** 效果是固定 50% 追加伤害，多级叠加没有意义，因此只有一级。 */
    @Override
    public int getMaxLevel()
    {
        return 1;
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
