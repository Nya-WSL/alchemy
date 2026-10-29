package com.nya_wsl.alchemy.enchantment;

import com.nya_wsl.alchemy.Config;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 保护mini：每级固定减少 {@link Config#miniProtectionReductionPerLevel} 点伤害（默认 0.5）。
 * <p>
 * 与原版「保护」不同，本附魔按等级相加结算：头盔 / 胸甲 / 护腿 / 靴子四件上的等级
 * 会累加（满级 10 x 4 件 = 20 点固定减伤）。减伤时机在原版护甲、保护类附魔、
 * 抗性药水之后，具体见 combat/MiniProtectionHandler。
 * <p>
 * 与原版保护、火焰保护等不互斥：{@link Enchantment#checkCompatibility} 的默认实现
 * 只排斥同种附魔，本类沿用该默认行为，因此可以和它们同时存在、效果叠加。
 */
public class MiniProtectionEnchantment extends Enchantment
{
    public MiniProtectionEnchantment()
    {
        super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @Override
    public int getMaxLevel()
    {
        return Math.max(1, Config.miniProtectionMaxLevel);
    }

    @Override
    public int getMinCost(int level)
    {
        // 曲线比原版保护平缓，让 10 级在附魔台上仍有机会出现
        return 10 + (level - 1) * 5;
    }

    @Override
    public int getMaxCost(int level)
    {
        return getMinCost(level) + 15 + (level - 1) * 5;
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
