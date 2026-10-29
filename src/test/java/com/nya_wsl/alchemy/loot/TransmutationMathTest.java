package com.nya_wsl.alchemy.loot;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 点石成金的纯计算部分：概率表取值、权重抽取、时运数量、宝石骰、堆叠拆分。
 * <p>
 * 这些函数刻意不碰任何 Minecraft 类型，所以能像 DoubleStrikeMath 那样直接跑单元测试，
 * 不需要启动游戏。
 */
class TransmutationMathTest
{
    private static final double EPSILON = 1.0E-9D;

    /** 设计文档第 7 节的三级概率表。 */
    private static final List<Double> CHANCES = List.of(0.005D, 0.010D, 0.020D);

    // ---------------------------------------------------------------- 概率表

    @Test
    @DisplayName("概率按等级取表：1 -> 0.5%，2 -> 1.0%，3 -> 2.0%")
    void chanceFollowsLevelTable()
    {
        assertEquals(0.005D, TransmutationMath.chanceFor(1, CHANCES, 0.0D), EPSILON);
        assertEquals(0.010D, TransmutationMath.chanceFor(2, CHANCES, 0.0D), EPSILON);
        assertEquals(0.020D, TransmutationMath.chanceFor(3, CHANCES, 0.0D), EPSILON);
    }

    @Test
    @DisplayName("等级超过表长时沿用最后一项")
    void levelBeyondTableUsesLastEntry()
    {
        assertEquals(0.020D, TransmutationMath.chanceFor(4, CHANCES, 0.0D), EPSILON);
        assertEquals(0.020D, TransmutationMath.chanceFor(10, CHANCES, 0.0D), EPSILON);
    }

    @Test
    @DisplayName("没有附魔等级或概率表为空时不触发")
    void noLevelOrEmptyTableMeansZero()
    {
        assertEquals(0.0D, TransmutationMath.chanceFor(0, CHANCES, 0.0D), EPSILON);
        assertEquals(0.0D, TransmutationMath.chanceFor(-2, CHANCES, 0.0D), EPSILON);
        assertEquals(0.0D, TransmutationMath.chanceFor(3, List.of(), 0.0D), EPSILON);
        assertEquals(0.0D, TransmutationMath.chanceFor(3, null, 0.0D), EPSILON);
    }

    @Test
    @DisplayName("表里的值 clamp 到 [0, 1]")
    void tableValuesAreClamped()
    {
        assertEquals(0.0D, TransmutationMath.chanceFor(1, List.of(-0.5D), 0.0D), EPSILON);
        assertEquals(1.0D, TransmutationMath.chanceFor(1, List.of(2.0D), 0.0D), EPSILON);
        // 表里混进 null 当作 0 处理，不让配置写崩直接抛异常
        assertEquals(0.0D, TransmutationMath.chanceFor(1, Collections.singletonList(null), 0.0D), EPSILON);
    }

    @Test
    @DisplayName("第 12 节的 bonusChance 会叠加到概率上，并同样 clamp 到 1")
    void bonusChanceIsAddedAndClamped()
    {
        // 将来「镐每级强化 +2% Midas 概率」就靠这个参数接进来
        assertEquals(0.025D, TransmutationMath.chanceFor(1, CHANCES, 0.02D), EPSILON);
        assertEquals(1.0D, TransmutationMath.chanceFor(3, CHANCES, 0.99D), EPSILON);
        // 负的加成不该把概率压到 0 以下
        assertEquals(0.0D, TransmutationMath.chanceFor(1, CHANCES, -0.5D), EPSILON);
    }

    // ---------------------------------------------------------------- 权重抽取

    @Test
    @DisplayName("按权重抽索引：每个区间落在对应条目上")
    void weightedIndexRespectsCumulativeWeights()
    {
        // 主世界表总权重 94
        List<Integer> weights = List.of(30, 20, 15, 10, 8, 6, 3, 2);

        assertEquals(94, TransmutationMath.totalWeight(weights));
        assertEquals(0, TransmutationMath.weightedIndex(weights, 0));
        assertEquals(0, TransmutationMath.weightedIndex(weights, 29));
        assertEquals(1, TransmutationMath.weightedIndex(weights, 30));
        assertEquals(1, TransmutationMath.weightedIndex(weights, 49));
        assertEquals(2, TransmutationMath.weightedIndex(weights, 50));
        assertEquals(7, TransmutationMath.weightedIndex(weights, 93));
    }

    @Test
    @DisplayName("空表 / 全零权重 / 越界 roll 返回 -1")
    void invalidPoolsYieldMinusOne()
    {
        assertEquals(-1, TransmutationMath.weightedIndex(List.of(), 0));
        assertEquals(-1, TransmutationMath.weightedIndex(List.of(0, 0), 0));
        assertEquals(-1, TransmutationMath.weightedIndex(List.of(5), 5));
        assertEquals(-1, TransmutationMath.weightedIndex(List.of(5), -1));
        assertEquals(0, TransmutationMath.totalWeight(List.of()));
    }

    @Test
    @DisplayName("权重为 0 的条目永远抽不到")
    void zeroWeightEntriesAreNeverPicked()
    {
        List<Integer> weights = List.of(5, 0, 5);

        assertEquals(0, TransmutationMath.weightedIndex(weights, 4));
        assertEquals(2, TransmutationMath.weightedIndex(weights, 5));
        assertEquals(2, TransmutationMath.weightedIndex(weights, 9));
    }

    @Test
    @DisplayName("下界表 9:1 的两个条目都能被抽到")
    void netherTableIsReachable()
    {
        List<Integer> weights = List.of(9, 1);

        assertEquals(0, TransmutationMath.weightedIndex(weights, 8));
        assertEquals(1, TransmutationMath.weightedIndex(weights, 9));
    }

    // ---------------------------------------------------------------- 时运

    @Test
    @DisplayName("没有时运时数量就是基础值")
    void noFortuneKeepsBaseCount()
    {
        assertEquals(1, TransmutationMath.fortuneMultiplier(0, 0));
        assertEquals(1, TransmutationMath.fortuneMultiplier(-3, 4));
        assertEquals(1, TransmutationMath.fortuneCount(1, 0, 0));
    }

    @Test
    @DisplayName("时运倍率 = max(1, nextInt(level+2))，与原版 ore_drops 等价")
    void fortuneMultiplierMatchesVanillaOreDrops()
    {
        // roll 是 nextInt(fortune + 2) 的结果，范围 [0, fortune+1]
        // 倍率 = max(1, roll)，所以 roll=0 和 roll=1 都得到 1（原版 nextInt-1 再夹 0 的等价形式）
        assertEquals(1, TransmutationMath.fortuneMultiplier(1, 0));
        assertEquals(1, TransmutationMath.fortuneMultiplier(1, 1));
        assertEquals(2, TransmutationMath.fortuneMultiplier(1, 2));
        assertEquals(3, TransmutationMath.fortuneMultiplier(2, 3));
        assertEquals(4, TransmutationMath.fortuneMultiplier(3, 4));
        assertEquals(6, TransmutationMath.fortuneMultiplier(5, 6));
    }

    @Test
    @DisplayName("时运 III 的均值是 2.2（不是 3.0）")
    void fortuneThreeAverageIsTwoPointTwo()
    {
        // 原版公式 i = max(nextInt(level+2) - 1, 0)，倍率 1..level+1
        // III 级 roll 在 [0,4] 上均匀，倍率依次 1,1,2,3,4，均值 11/5 = 2.2
        double sum = 0.0D;
        for (int roll = 0; roll <= 4; roll++)
        {
            sum += TransmutationMath.fortuneMultiplier(3, roll);
        }

        assertEquals(2.2D, sum / 5.0D, EPSILON);
    }

    @Test
    @DisplayName("时运等级 clamp 到 [0, 5]")
    void fortuneLevelIsClamped()
    {
        // 超过 5 级按 5 级算：roll 上限是 6
        assertEquals(6, TransmutationMath.fortuneMultiplier(10, 6));
        // 配置写崩成负数时不让数量归零
        assertEquals(1, TransmutationMath.fortuneCount(1, -1, 0));
    }

    @Test
    @DisplayName("数量上限 64，单个堆叠不超过 64")
    void countIsClampedToStackSize()
    {
        assertEquals(64, TransmutationMath.fortuneCount(64, 5, 6));
        assertEquals(64, TransmutationMath.clampCount(1000));
        assertEquals(0, TransmutationMath.clampCount(-5));
        assertEquals(1, TransmutationMath.clampCount(1));
    }

    @Test
    @DisplayName("超过 64 时拆成多堆，每堆不超过 64")
    void oversizedCountSplitsIntoStacks()
    {
        assertEquals(List.of(1), TransmutationMath.splitIntoStacks(1));
        assertEquals(List.of(64), TransmutationMath.splitIntoStacks(64));
        assertEquals(List.of(64, 1), TransmutationMath.splitIntoStacks(65));
        assertEquals(List.of(64, 64, 2), TransmutationMath.splitIntoStacks(130));
        assertEquals(List.of(), TransmutationMath.splitIntoStacks(0));
    }

    // ---------------------------------------------------------------- 宝石骰

    @Test
    @DisplayName("宝石只在等级达到起点且概率为正时才判定")
    void gemRollRequiresLevelAndChance()
    {
        assertTrue(TransmutationMath.shouldRollGem(3, 3, 0.02D));
        assertTrue(TransmutationMath.shouldRollGem(5, 3, 0.02D));
        assertFalse(TransmutationMath.shouldRollGem(2, 3, 0.02D));
        assertFalse(TransmutationMath.shouldRollGem(1, 3, 0.02D));
        assertFalse(TransmutationMath.shouldRollGem(3, 3, 0.0D));
        assertFalse(TransmutationMath.shouldRollGem(3, 3, -0.1D));
    }

    @Test
    @DisplayName("宝石起点等级高于最大等级时视为不掉宝石")
    void gemStartLevelAboveMaxLevelDisablesGems()
    {
        assertFalse(TransmutationMath.shouldRollGem(3, 9, 0.02D));
        assertEquals(0.0D, TransmutationMath.gemChanceFor(3, 9, 0.02D), EPSILON);
        assertEquals(0.02D, TransmutationMath.gemChanceFor(3, 3, 0.02D), EPSILON);
        // 概率本身也要 clamp
        assertEquals(1.0D, TransmutationMath.gemChanceFor(3, 3, 5.0D), EPSILON);
    }
}
