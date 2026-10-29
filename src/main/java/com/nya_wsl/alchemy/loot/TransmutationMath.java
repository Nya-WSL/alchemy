package com.nya_wsl.alchemy.loot;

import java.util.ArrayList;
import java.util.List;

/**
 * 点石成金的纯计算层。
 * <p>
 * 全部是静态方法、只吃基本类型和 {@link List}，刻意不碰任何 Minecraft 类型——
 * 这样概率表、权重抽取、时运公式、宝石判定、堆叠拆分都能跑单元测试，
 * 不需要启动游戏（和 {@code DoubleStrikeMath} / {@code MiniProtectionMath} 同样的思路）。
 * <p>
 * 注意这里<b>不含随机源</b>：所有需要随机的地方都由调用方传进 {@code roll} 或由调用方掷骰，
 * 好处是测试能精确控制边界，而 {@code StoneTransmutationModifier} 用 {@code context.getRandom()} 驱动。
 */
public final class TransmutationMath
{
    /** 原版单个物品堆叠上限，数量超过它要拆堆。 */
    public static final int MAX_STACK_SIZE = 64;

    /** 时运等级上限，与原版 {@code ore_drops} 的 clamp 一致。 */
    public static final int MAX_FORTUNE_LEVEL = 5;

    private TransmutationMath()
    {
    }

    // ---------------------------------------------------------------- 概率表

    /**
     * 取某个等级对应的触发概率。
     * <p>
     * 规则（设计文档第 7 节）：
     * <ul>
     *   <li>等级小于 1 或概率表为空 → 0，不触发。</li>
     *   <li>等级超出表长时<b>沿用最后一项</b>——这样 {@code maxLevel} 调大而表没跟着补时，
     *       行为是"封顶"而不是"失效"，出了问题也更容易发现。</li>
     *   <li>表项 clamp 到 {@code [0, 1]}；null 当 0 处理，避免配置写崩直接抛异常。</li>
     * </ul>
     *
     * @param level      附魔等级
     * @param chances    概率表，第 i 项对应 i+1 级
     * @param bonusChance 额外加成的概率（第 12 节 Midas 钩子，本次传 0）
     * @return 最终概率，落在 {@code [0, 1]}
     */
    public static double chanceFor(final int level, final List<Double> chances, final double bonusChance)
    {
        if (level < 1 || chances == null || chances.isEmpty())
        {
            return 0.0D;
        }

        // 等级 1 取 index 0；超过表长时钉在最后一项
        final int index = Math.min(level, chances.size()) - 1;
        final Double entry = chances.get(index);
        final double base = entry == null ? 0.0D : entry;

        return clamp01(base + bonusChance);
    }

    /**
     * 宝石概率：等级没到起点、或起点高于最大等级时都返回 0（视为不掉宝石）。
     *
     * @param level             附魔等级
     * @param gemChanceStartLevel 开始掉宝石的等级
     * @param gemChance          宝石概率
     * @return 落在 {@code [0, 1]} 的宝石概率
     */
    public static double gemChanceFor(final int level, final int gemChanceStartLevel, final double gemChance)
    {
        if (level < 1 || level < gemChanceStartLevel)
        {
            return 0.0D;
        }

        return clamp01(gemChance);
    }

    /**
     * 这次是否该掷宝石骰。
     * <p>
     * 与转化骰<b>完全独立</b>（设计文档 3.6 / 8.6）：转化失败时宝石骰照掷，
     * 所以本方法只看等级和概率，不看转化是否命中。
     */
    public static boolean shouldRollGem(final int level, final int gemChanceStartLevel, final double gemChance)
    {
        return gemChanceFor(level, gemChanceStartLevel, gemChance) > 0.0D;
    }

    private static double clamp01(final double value)
    {
        if (Double.isNaN(value))
        {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, value));
    }

    // ---------------------------------------------------------------- 权重抽取

    /** 权重表的总权重；空表为 0。负权重按 0 计，防止把总权重算小导致 roll 越界。 */
    public static int totalWeight(final List<Integer> weights)
    {
        if (weights == null)
        {
            return 0;
        }

        int total = 0;
        for (final Integer weight : weights)
        {
            if (weight != null && weight > 0)
            {
                total += weight;
            }
        }

        return total;
    }

    /**
     * 按累计权重把 {@code roll} 映射成条目下标。
     *
     * @param weights 权重表
     * @param roll    调用方掷出的 {@code [0, totalWeight)} 上的整数
     * @return 条目下标；空表、全零权重、roll 越界时返回 -1
     */
    public static int weightedIndex(final List<Integer> weights, final int roll)
    {
        if (weights == null || roll < 0)
        {
            return -1;
        }

        int cumulative = 0;
        for (int i = 0; i < weights.size(); i++)
        {
            final Integer weight = weights.get(i);
            if (weight == null || weight <= 0)
            {
                continue;
            }

            cumulative += weight;
            if (roll < cumulative)
            {
                return i;
            }
        }

        // roll 落在总权重之外：调用方算错了总权重，返回 -1 让上层跳过而不是悄悄取最后一项
        return -1;
    }

    // ---------------------------------------------------------------- 时运

    /**
     * 时运倍率，等价于原版 {@code ore_drops} 的 {@code i = max(nextInt(level + 2) - 1, 0)}。
     * <p>
     * 原版是先 {@code nextInt(level+2)} 再减 1、再夹到 0，所以倍率范围是 {@code 1..level+1}，
     * 且 roll=0 和 roll=1 都映射到 1。这里把"减 1 + 夹 0"合成为 {@code max(1, roll)}，
     * 由调用方传入 {@code nextInt(fortune + 2)} 的结果。
     * <p>
     * III 级均值 2.2（不是 3.0）——多算这一步会让产量系统性偏高 36%。
     *
     * @param fortuneLevel 时运等级（会 clamp 到 {@code [0, 5]}）
     * @param roll         {@code nextInt(fortuneLevel + 2)} 的结果
     * @return 倍率，至少为 1
     */
    public static int fortuneMultiplier(final int fortuneLevel, final int roll)
    {
        if (fortuneLevel <= 0)
        {
            return 1;
        }

        return Math.max(1, roll);
    }

    /**
     * 时运加成后的数量：{@code base * 倍率}，再 clamp 到 64。
     *
     * @param baseCount    基础数量
     * @param fortuneLevel 时运等级
     * @param roll         {@code nextInt(clamp(fortuneLevel) + 2)} 的结果
     * @return 落在 {@code [0, 64]} 的数量
     */
    public static int fortuneCount(final int baseCount, final int fortuneLevel, final int roll)
    {
        if (baseCount <= 0)
        {
            return 0;
        }

        final int clampedFortune = Math.max(0, Math.min(MAX_FORTUNE_LEVEL, fortuneLevel));
        final int multiplier = fortuneMultiplier(clampedFortune, roll);
        return clampCount((long) baseCount * multiplier);
    }

    /** 把数量夹到 {@code [0, 64]}。 */
    public static int clampCount(final long count)
    {
        return (int) Math.max(0L, Math.min(MAX_STACK_SIZE, count));
    }

    /**
     * 把数量拆成若干堆，每堆不超过 {@link #MAX_STACK_SIZE}。
     * <p>
     * 设计文档第 8 节要求"超出 64 拆成多堆"。当前物品表全部是 {@code maxStackSize=64}，
     * 所以这里统一按 64 拆。
     */
    public static List<Integer> splitIntoStacks(final int totalCount)
    {
        final List<Integer> stacks = new ArrayList<>();
        int remaining = Math.max(0, totalCount);
        while (remaining > 0)
        {
            final int size = Math.min(MAX_STACK_SIZE, remaining);
            stacks.add(size);
            remaining -= size;
        }

        return stacks;
    }
}
