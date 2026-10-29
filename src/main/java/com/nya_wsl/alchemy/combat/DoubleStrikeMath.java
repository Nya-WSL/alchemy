package com.nya_wsl.alchemy.combat;

/**
 * 「双重打击」的伤害换算，抽成纯函数便于单元测试。
 * <p>
 * 规则：第二段伤害 = 第一段实际造成的伤害 × {@link #BONUS_RATIO}（默认 50%）。
 * 这里的「原伤害」指玩家这一剑真正打出去的数值，已经在 {@code Player#attack} 里算完了
 * 攻击冷却系数、力量 / 锋利加成与暴击倍率，但不含受击者自身的护甲与抗性——
 * 第二段会作为一个独立伤害再走一遍完整的受击结算，因此护甲、抗性、保护mini 都会照常生效。
 */
public final class DoubleStrikeMath
{
    /** 第二段伤害相对第一段的比例（50%）。 */
    public static final double BONUS_RATIO = 0.5D;

    private DoubleStrikeMath()
    {
    }

    /**
     * 算出第二段该打多少伤害。
     *
     * @param originalDamage 第一段打出的伤害
     * @param ratio          追加比例（小于 0 按 0 处理）
     * @return 第二段伤害，非正输入一律返回 0（不会变成治疗或负伤害）
     */
    public static float bonusDamage(final float originalDamage, final double ratio)
    {
        if (originalDamage <= 0.0F || ratio <= 0.0D)
        {
            return 0.0F;
        }

        return (float) (originalDamage * ratio);
    }
}
