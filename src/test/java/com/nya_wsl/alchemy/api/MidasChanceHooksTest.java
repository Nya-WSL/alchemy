package com.nya_wsl.alchemy.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉住 Midas 扩展点的三条不变量。
 * <p>
 * 为什么要有这组测试：前置模组单独运行时，{@code StoneTransmutationModifier} 的概率必须
 * 和加钩子之前逐位相同——谁把 1.0 这个中性值改坏，都会让点石成金静默变强或变废，
 * 而那种偏差在游戏里几乎不可能靠手感发现。
 * <p>
 * 这里刻意传 {@code null} 而不传 {@link net.minecraft.world.item.ItemStack#EMPTY}：
 * 后者的静态初始化要求 Minecraft 已 bootstrap，会在纯 JUnit 环境里抛
 * {@code Not bootstrapped}。传 null 既满足签名，又让本测试保持"不启动游戏"的既有纪律。
 */
class MidasChanceHooksTest
{
    @BeforeEach
    void reset()
    {
        MidasChanceHooks.clearForTesting();
    }

    @AfterEach
    void tearDown()
    {
        MidasChanceHooks.clearForTesting();
    }

    @Test
    @DisplayName("没有任何注册者时倍率为 1.0，前置模组行为不变")
    void noProviderMeansNeutralMultiplier()
    {
        assertEquals(1.0D, MidasChanceHooks.multiplier(null, null, 5), 1.0E-9);
    }

    @Test
    @DisplayName("多个 provider 的倍率相乘")
    void providersMultiply()
    {
        MidasChanceHooks.register((player, tool, level) -> 1.5D);
        MidasChanceHooks.register((player, tool, level) -> 2.0D);

        assertEquals(3.0D, MidasChanceHooks.multiplier(null, null, 5), 1.0E-9);
    }

    @Test
    @DisplayName("provider 返回 NaN 或无穷时被忽略，不会把概率链污染成 NaN")
    void nonFiniteMultipliersAreIgnored()
    {
        MidasChanceHooks.register((player, tool, level) -> Double.NaN);
        MidasChanceHooks.register((player, tool, level) -> Double.POSITIVE_INFINITY);
        MidasChanceHooks.register((player, tool, level) -> 2.0D);

        assertEquals(2.0D, MidasChanceHooks.multiplier(null, null, 5), 1.0E-9);
    }
}
