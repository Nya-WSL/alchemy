package com.nya_wsl.alchemy.combat;

/**
 * 保护mini 的伤害换算，抽成纯函数便于单元测试。
 * <p>
 * 规则：每级固定减伤 {@code perLevel}（默认 0.5），四件装备的等级相加后一起算总减伤；
 * 减伤从「已经过原版护甲 / 附魔保护 / 抗性计算之后」的伤害里扣，最多扣到 0。
 */
public final class MiniProtectionMath
{
    private MiniProtectionMath()
    {
    }

    /**
     * 把总附魔等级换算成减伤点数。
     *
     * @param totalLevels 四件装备上的等级之和（负数按 0 处理）
     * @param perLevel    每级减伤点数（小于 0 按 0 处理）
     * @return 总减伤点数
     */
    public static float reductionFor(final int totalLevels, final double perLevel)
    {
        if (totalLevels <= 0 || perLevel <= 0.0D)
        {
            return 0.0F;
        }

        return (float) (totalLevels * perLevel);
    }

    /**
     * 从伤害里扣掉减伤，结果不小于 0（不会变成治疗）。伤害本身非正时原样返回。
     *
     * @param damage    当前伤害
     * @param reduction 减伤点数
     * @return 扣减后的伤害
     */
    public static float applyReduction(final float damage, final float reduction)
    {
        if (damage <= 0.0F || reduction <= 0.0F)
        {
            return damage;
        }

        return Math.max(damage - reduction, 0.0F);
    }
}
