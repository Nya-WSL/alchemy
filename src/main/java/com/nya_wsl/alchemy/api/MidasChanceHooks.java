package com.nya_wsl.alchemy.api;

import com.nya_wsl.alchemy.ModMain;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 「点石成金触发概率倍率」的扩展点。
 * <p>
 * 为什么需要一个注册表而不是配置项：强化系统（另一个模组）想按镐的强化等级提升触发概率，
 * 但那套等级表属于对方模组的运行期状态，前置模组既不该知道、也不该在每次挖掘时去反射查它。
 * 把「倍率从哪来」翻转成由外部注册，前置模组只保留乘法和一个中性默认值，
 * 于是<b>没有注册者时概率与加钩子之前逐位相同</b>——这是本类的核心不变量，有测试钉住。
 * <p>
 * 线程安全：{@link CopyOnWriteArrayList} 让注册（模组加载期、单线程）与读取（多线程的掉落计算）
 * 不必互相等待；注册次数是个位数，写时复制的成本可以直接忽略。
 */
public final class MidasChanceHooks
{
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private MidasChanceHooks()
    {
    }

    /** 概率倍率来源。返回 1.0 表示不干预；多个来源的倍率相乘。 */
    @FunctionalInterface
    public interface Provider
    {
        /**
         * @param player       挖掘的玩家，可能为 null（例如非玩家触发的掉落）
         * @param tool         使用的工具；可能为 null，调用方拿不到工具上下文时会这样传
         * @param enchantLevel 点石成金附魔等级
         * @return 概率倍率，1.0 为中性；非有限值会被忽略
         */
        double chanceMultiplier(@Nullable Player player, @Nullable ItemStack tool, int enchantLevel);
    }

    /** 注册一个倍率 provider。重复注册同一个实例会被忽略，避免模组重载时倍率被重复相乘。 */
    public static void register(final Provider provider)
    {
        if (provider == null)
        {
            return;
        }

        if (!PROVIDERS.contains(provider))
        {
            PROVIDERS.add(provider);
        }
    }

    /**
     * 所有 provider 的倍率<b>相乘</b>；没有注册者时返回 1.0。
     * <p>
     * 为什么不相加：倍率相乘能让「两个来源各自翻倍」表达成 4 倍，
     * 而相加在概率上限附近会把结果压平，也无法表达乘法语义。
     * <p>
     * 非有限值（NaN / Infinity）会让整条概率链变成 NaN——{@code clamp01} 之后是 0，
     * 表现为点石成金彻底失效且毫无线索。所以这里逐项校验并 warn，而不是让它悄悄传播。
     */
    public static double multiplier(@Nullable final Player player,
                                    @Nullable final ItemStack tool,
                                    final int enchantLevel)
    {
        double result = 1.0D;
        for (final Provider provider : PROVIDERS)
        {
            final double value = provider.chanceMultiplier(player, tool, enchantLevel);
            if (!Double.isFinite(value))
            {
                ModMain.LOGGER.warn("alchemy: Midas 倍率 provider {} 返回了非有限值 {}，已忽略。",
                        provider.getClass().getName(), value);
                continue;
            }

            result *= value;
        }

        return result;
    }

    /** 仅供测试：清空注册表，避免用例之间互相串扰。 */
    static void clearForTesting()
    {
        PROVIDERS.clear();
    }
}
