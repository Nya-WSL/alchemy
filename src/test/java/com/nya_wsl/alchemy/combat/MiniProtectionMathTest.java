package com.nya_wsl.alchemy.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 保护mini 的纯计算部分：等级换算总减伤、以及从伤害里扣减并夹到 0。
 * 这部分不依赖 Minecraft 运行期，所以能直接跑单元测试。
 */
class MiniProtectionMathTest
{
    private static final float EPSILON = 1.0E-4F;

    @Test
    @DisplayName("没有附魔等级时不减伤")
    void noLevelsMeansNoReduction()
    {
        assertEquals(0.0F, MiniProtectionMath.reductionFor(0, 0.5D), EPSILON);
        assertEquals(0.0F, MiniProtectionMath.reductionFor(-3, 0.5D), EPSILON);
    }

    @Test
    @DisplayName("每级固定 0.5：等级 x 0.5")
    void reductionScalesLinearly()
    {
        assertEquals(0.5F, MiniProtectionMath.reductionFor(1, 0.5D), EPSILON);
        assertEquals(1.5F, MiniProtectionMath.reductionFor(3, 0.5D), EPSILON);
        assertEquals(5.0F, MiniProtectionMath.reductionFor(10, 0.5D), EPSILON);
    }

    @Test
    @DisplayName("四件装备等级相加：满级 10 x 4 件 = 20 点减伤")
    void sumsAcrossArmorPieces()
    {
        int totalLevels = 10 + 10 + 10 + 10;
        assertEquals(20.0F, MiniProtectionMath.reductionFor(totalLevels, 0.5D), EPSILON);
    }

    @Test
    @DisplayName("每级减伤为 0 时不减伤")
    void zeroPerLevelMeansNoReduction()
    {
        assertEquals(0.0F, MiniProtectionMath.reductionFor(10, 0.0D), EPSILON);
        assertEquals(0.0F, MiniProtectionMath.reductionFor(10, -1.0D), EPSILON);
    }

    @Test
    @DisplayName("减伤从伤害里扣掉")
    void reductionIsSubtracted()
    {
        assertEquals(4.5F, MiniProtectionMath.applyReduction(7.0F, 2.5F), EPSILON);
        assertEquals(0.5F, MiniProtectionMath.applyReduction(1.0F, 0.5F), EPSILON);
    }

    @Test
    @DisplayName("减伤超过伤害时夹到 0，不会变成治疗")
    void neverGoesNegative()
    {
        assertEquals(0.0F, MiniProtectionMath.applyReduction(1.0F, 5.0F), EPSILON);
        assertEquals(0.0F, MiniProtectionMath.applyReduction(0.5F, 20.0F), EPSILON);
    }

    @Test
    @DisplayName("伤害本来就是 0 或负数时原样返回")
    void nonPositiveDamagePassesThrough()
    {
        assertEquals(0.0F, MiniProtectionMath.applyReduction(0.0F, 2.5F), EPSILON);
        assertEquals(-1.0F, MiniProtectionMath.applyReduction(-1.0F, 2.5F), EPSILON);
    }
}
