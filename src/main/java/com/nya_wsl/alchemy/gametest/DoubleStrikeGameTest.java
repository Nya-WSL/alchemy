package com.nya_wsl.alchemy.gametest;

import com.nya_wsl.alchemy.ModMain;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 「双重打击」的 GameTest 回归测试。
 * <p>
 * 为什么必须用 GameTest 而不是 RCON 脚本：本附魔只在「玩家本人用近战武器直接造成伤害」时触发，
 * 而 RCON 控制台执行 {@code /damage} 时伤害来源没有 {@code Entity}，走不进这条分支。
 * 只有 {@link GameTestHelper#makeMockSurvivalPlayer()} 造出的假玩家能真的调用
 * {@code Player#attack}，从 {@code DamageSources#playerAttack} 一路触发到本模组的处理器。
 * <p>
 * 关于竞技场坐标：模板 {@code data/alchemy/structures/double_strike_arena.nbt} 是一层 5×5 的石头地板，
 * 但结构方块加载时会把模板内容整体挪到 {@code 结构方块坐标 + (0,1,0)}
 * （{@code StructureBlockEntity#loadStructure} 里的 {@code blockpos.offset(this.structurePos)}，
 * 而 {@code structurePos} 默认是 {@code (0,1,0)}）。{@link GameTestHelper#absolutePos} 的原点却是结构方块本身，
 * 因此模板里的 {@code y=0} 石头实际位于<b>相对 y=1</b>，实体的脚要放在<b>相对 y=2</b>。
 * 放错一层会让假人卡进石头里持续窒息，血量变化就完全不可信了——{@link #assertArenaFloor} 专门守着这条。
 * <p>
 * 用了 {@link PrefixGameTestTemplate @PrefixGameTestTemplate(false)}：否则框架会在模板名前面自动加上
 * {@code 类名小写.} 前缀，解析出 {@code alchemy:doublestrikegametest.double_strike_arena}，
 * 找不到文件就直接抛 "Could not find structure file"。
 */
@GameTestHolder(ModMain.MODID)
@PrefixGameTestTemplate(false)
public final class DoubleStrikeGameTest
{
    /** 目标假人的血量：够高，保证一个测试里吃几刀也不会死。 */
    private static final double TARGET_MAX_HEALTH = 200.0D;

    /** 第二段应当是原伤害的 50%，所以总伤害比是 1.5。 */
    private static final float EXPECTED_TOTAL_RATIO = 1.5F;

    /** 浮点比较的容差：护甲已清零，只剩 float 本身的舍入误差。 */
    private static final float EPSILON = 1.0E-3F;

    /**
     * 假玩家的攻击力。
     * <p>
     * 必须显式设定：{@code makeMockSurvivalPlayer()} 返回的玩家不会 tick，装备属性修饰符也不会结算，
     * {@code ATTACK_DAMAGE} 会停留在默认的 1.0。再乘上不到 1 的攻击蓄力系数，
     * 每一刀只有 0.2 左右——这个量级下"单段还是双段"的差别小到会被浮点误差和取整吞掉，
     * 断言就退化成恒真。抬到 40 之后单段约 8 点，1.5 倍的差距一眼可辨。
     */
    private static final double MOCK_ATTACK_DAMAGE = 40.0D;

    /** 实体站立的高度（相对坐标）。见类注释里对 {@code (0,1,0)} 偏移的说明。 */
    private static final int STAND_Y = 2;

    private DoubleStrikeGameTest()
    {
    }

    /**
     * 附魔剑一次挥击造成的总伤害应当正好是白板剑的 1.5 倍，并且 1 tick 后就能看到。
     * <p>
     * 「1 tick 内就看到 1.5 倍」本身就是同 tick 落地的证明：原版第一刀会把目标的
     * {@code invulnerableTime} 置为 20，如果实现是等无敌帧结束再补刀，那么 1 tick 后血量只可能掉一倍。
     * <p>
     * 两个目标各自配一个独立假玩家——处理器按「同 tick 同玩家」去重（防止横扫重复触发），
     * 同一个玩家在同一 tick 打两个目标只会结算一次。
     */
    @GameTest(template = "double_strike_arena", timeoutTicks = 200)
    public static void bonusDamageLandsInSameTick(final GameTestHelper helper)
    {
        assertArenaFloor(helper);

        final Husk plainTarget = spawnTarget(helper, 1, 1);
        final Husk enchantedTarget = spawnTarget(helper, 3, 3);

        final Player plainPlayer = armedPlayer(helper, plainTarget, false);
        final Player enchantedPlayer = armedPlayer(helper, enchantedTarget, true);

        final float plainBefore = plainTarget.getHealth();
        final float enchantedBefore = enchantedTarget.getHealth();

        plainPlayer.attack(plainTarget);
        enchantedPlayer.attack(enchantedTarget);

        helper.runAfterDelay(1L, () ->
        {
            final float plainDelta = plainBefore - plainTarget.getHealth();
            final float enchantedDelta = enchantedBefore - enchantedTarget.getHealth();

            helper.assertTrue(plainDelta > 0.0F,
                    "白板剑没有造成伤害，测试前提不成立：" + plainDelta);
            helper.assertTrue(enchantedDelta > plainDelta,
                    "附魔剑的伤害没有高于白板剑：" + enchantedDelta + " <= " + plainDelta);

            final float ratio = enchantedDelta / plainDelta;
            helper.assertTrue(Math.abs(ratio - EXPECTED_TOTAL_RATIO) <= EPSILON * EXPECTED_TOTAL_RATIO,
                    "期望总伤害为白板剑的 " + EXPECTED_TOTAL_RATIO + " 倍，实际为 " + ratio
                            + "（白板 " + plainDelta + "，附魔 " + enchantedDelta + "）");

            helper.succeed();
        });
    }

    /**
     * 一次挥击要消耗两倍耐久：原版本体扣 1 点，第二段再补 1 点。
     * <p>
     * 用的是没附「耐久」的钻石剑，假玩家是生存模式——{@code ItemStack#hurtAndBreak} 只在创造模式或
     * 客户端时才跳过，所以扣耐久确实会发生。
     */
    @GameTest(template = "double_strike_arena", timeoutTicks = 200)
    public static void extraDurabilityConsumedPerSwing(final GameTestHelper helper)
    {
        assertArenaFloor(helper);

        final Husk plainTarget = spawnTarget(helper, 1, 1);
        final Husk enchantedTarget = spawnTarget(helper, 3, 3);

        final Player plainPlayer = armedPlayer(helper, plainTarget, false);
        final Player enchantedPlayer = armedPlayer(helper, enchantedTarget, true);

        plainPlayer.attack(plainTarget);
        enchantedPlayer.attack(enchantedTarget);

        helper.runAfterDelay(1L, () ->
        {
            final int plainDamage = plainPlayer.getMainHandItem().getDamageValue();
            final int enchantedDamage = enchantedPlayer.getMainHandItem().getDamageValue();

            helper.assertTrue(plainDamage == 1,
                    "白板剑一次挥击应当只扣 1 点耐久，实际扣了 " + plainDamage);
            helper.assertTrue(enchantedDamage == 2,
                    "附魔剑一次挥击应当扣 2 点耐久（本体 1 + 双重打击 1），实际扣了 " + enchantedDamage);

            helper.succeed();
        });
    }

    /**
     * 第二段只结算一次，之后不再追加。
     * <p>
     * 处理器用「同 tick 同玩家」去重来避免横扫重复触发，并且把第二段排进同 tick 的
     * {@code ServerTickEvent.END}。如果去重写错、或者排队条目没被正确移除，
     * 第二段就会在之后每个 tick 反复打，血量会持续往下掉。
     * 所以这里隔 20 tick 再量一次：两次总量必须一致。
     */
    @GameTest(template = "double_strike_arena", timeoutTicks = 200)
    public static void bonusAppliesExactlyOnce(final GameTestHelper helper)
    {
        assertArenaFloor(helper);

        final Husk target = spawnTarget(helper, 1, 1);
        final Player player = armedPlayer(helper, target, true);

        final float before = target.getHealth();
        player.attack(target);

        helper.runAfterDelay(1L, () ->
        {
            final float deltaAfterOneTick = before - target.getHealth();
            helper.assertTrue(deltaAfterOneTick > 0.0F,
                    "没有造成伤害，测试前提不成立：" + deltaAfterOneTick);

            helper.runAfterDelay(20L, () ->
            {
                final float deltaLater = before - target.getHealth();
                helper.assertTrue(Math.abs(deltaLater - deltaAfterOneTick) <= EPSILON,
                        "第二段伤害被重复结算了：1 tick 后共 " + deltaAfterOneTick
                                + "，21 tick 后变成 " + deltaLater);

                helper.succeed();
            });
        });
    }

    /**
     * 非玩家来源的伤害不该被加倍。
     * <p>
     * 让一只尸壳对目标造成 6 点伤害：伤害来源的 {@code getDirectEntity()} 不是玩家，
     * 处理器应当在第一步就返回。这条用例守的是「把玩家判定删掉」这类回归。
     * 攻击者用尸壳而不是僵尸：僵尸白天会被晒着起火，虽然不影响目标血量，但会让日志噪声很大。
     */
    @GameTest(template = "double_strike_arena", timeoutTicks = 200)
    public static void nonPlayerDamageIsUnaffected(final GameTestHelper helper)
    {
        assertArenaFloor(helper);

        final Husk target = spawnTarget(helper, 2, 2);
        final Husk attacker = helper.spawnWithNoFreeWill(EntityType.HUSK, 0, STAND_Y, 0);

        final float before = target.getHealth();
        target.hurt(helper.getLevel().damageSources().mobAttack(attacker), 6.0F);

        helper.runAfterDelay(1L, () ->
        {
            final float delta = before - target.getHealth();
            helper.assertTrue(Math.abs(delta - 6.0F) <= EPSILON,
                    "非玩家伤害不应被双重打击加倍，期望 6.0，实际 " + delta);

            helper.succeed();
        });
    }

    /**
     * 断言竞技场地板确实在预期的相对坐标上。
     * <p>
     * 如果这条先失败，说明模板的落位和这里推导的坐标不一致——那么后面那些血量断言就完全没有意义，
     * 直接给出明确的原因比让假人悄悄窒息要好得多。
     */
    private static void assertArenaFloor(final GameTestHelper helper)
    {
        for (int x = 0; x < 5; x++)
        {
            for (int z = 0; z < 5; z++)
            {
                final BlockPos floor = new BlockPos(x, STAND_Y - 1, z);
                helper.assertTrue(helper.getBlockState(floor).is(Blocks.STONE),
                        "竞技场地板缺失：相对坐标 " + floor + " 应当是石头，实际是 "
                                + helper.getBlockState(floor));
            }
        }
    }

    /**
     * 在竞技场里放一个不会动、不会还手、护甲清零的目标假人。
     * <p>
     * 护甲必须清零：原版减伤公式里 {@code clamp(armor - damage / (2 + toughness / 4), ...)} 对
     * 不同伤害量是非线性的，留着护甲会让「1.5 倍」这个比值本身就有偏差，测不出真实行为。
     */
    private static Husk spawnTarget(final GameTestHelper helper, final int x, final int z)
    {
        final Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, x, STAND_Y, z);

        final AttributeInstance maxHealth = husk.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null)
        {
            maxHealth.setBaseValue(TARGET_MAX_HEALTH);
        }

        final AttributeInstance armor = husk.getAttribute(Attributes.ARMOR);
        if (armor != null)
        {
            armor.setBaseValue(0.0D);
        }

        final AttributeInstance armorToughness = husk.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armorToughness != null)
        {
            armorToughness.setBaseValue(0.0D);
        }

        husk.setHealth((float) TARGET_MAX_HEALTH);
        return husk;
    }

    /**
     * 造一个拿着（可选附魔的）钻石剑的生存模式假玩家，并挪到目标旁边。
     * <p>
     * 假玩家不会被放进 {@code ServerLevel} 的实体表，也没有 tick，所以
     * {@code attackStrengthTicker} 恒为 0、{@code getAttackStrengthScale(0.5F)} 每次都是同一个值。
     * 两次攻击因此吃完全相同的伤害缩放，上面那个 1.5 倍的比较才有意义。
     */
    private static Player armedPlayer(final GameTestHelper helper, final Husk target, final boolean enchanted)
    {
        final ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        if (enchanted)
        {
            sword.enchant(ModEnchantments.DOUBLE_STRIKE.get(), 1);
        }

        final Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);

        final AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null)
        {
            attackDamage.setBaseValue(MOCK_ATTACK_DAMAGE);
        }

        player.moveTo(target.getX() - 1.0D, target.getY(), target.getZ(), 0.0F, 0.0F);
        return player;
    }
}
