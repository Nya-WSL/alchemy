package com.nya_wsl.alchemy.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 双重打击的纯计算部分：第二段伤害 = 原伤害 x 50%。
 * 这部分不依赖 Minecraft 运行期，所以能直接跑单元测试。
 */
class DoubleStrikeMathTest
{
    private static final float EPSILON = 1.0E-4F;

    @Test
    @DisplayName("第二段就是原伤害的一半")
    void bonusIsHalfOfOriginal()
    {
        assertEquals(3.5F, DoubleStrikeMath.bonusDamage(7.0F, 0.5D), EPSILON);
        assertEquals(1.0F, DoubleStrikeMath.bonusDamage(2.0F, 0.5D), EPSILON);
        assertEquals(0.5F, DoubleStrikeMath.bonusDamage(1.0F, 0.5D), EPSILON);
    }

    @Test
    @DisplayName("默认比例常量就是 0.5")
    void defaultRatioIsHalf()
    {
        assertEquals(0.5D, DoubleStrikeMath.BONUS_RATIO, 1.0E-9D);
        assertEquals(6.0F, DoubleStrikeMath.bonusDamage(12.0F, DoubleStrikeMath.BONUS_RATIO), EPSILON);
    }

    @Test
    @DisplayName("暴击后的大伤害同样对折")
    void critDamageIsHalvedToo()
    {
        // 钻石剑暴击跳劈的实际数值可能到两位数，第二段照样是它的一半
        assertEquals(7.5F, DoubleStrikeMath.bonusDamage(15.0F, 0.5D), EPSILON);
        assertEquals(18.0F, DoubleStrikeMath.bonusDamage(36.0F, 0.5D), EPSILON);
    }

    @Test
    @DisplayName("伤害为 0 或负数时不算第二段")
    void nonPositiveDamageYieldsZero()
    {
        assertEquals(0.0F, DoubleStrikeMath.bonusDamage(0.0F, 0.5D), EPSILON);
        assertEquals(0.0F, DoubleStrikeMath.bonusDamage(-4.0F, 0.5D), EPSILON);
    }

    @Test
    @DisplayName("比例为 0 或负数时不算第二段")
    void nonPositiveRatioYieldsZero()
    {
        assertEquals(0.0F, DoubleStrikeMath.bonusDamage(10.0F, 0.0D), EPSILON);
        assertEquals(0.0F, DoubleStrikeMath.bonusDamage(10.0F, -0.5D), EPSILON);
    }

    @Test
    @DisplayName("比例可以调大，用于将来做等级化")
    void ratioIsConfigurable()
    {
        assertEquals(10.0F, DoubleStrikeMath.bonusDamage(10.0F, 1.0D), EPSILON);
        assertEquals(2.5F, DoubleStrikeMath.bonusDamage(10.0F, 0.25D), EPSILON);
    }
}
