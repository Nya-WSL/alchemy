package com.nya_wsl.alchemy.combat;

import com.nya_wsl.alchemy.ModDamageTypes;
import com.nya_wsl.alchemy.ModMain;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「双重打击」的实战结算。
 * <p>
 * 一把带本附魔的剑命中目标时，同一 tick 内立刻再补一次相当于原伤害 50% 的伤害，同时多消耗一倍耐久。
 * <p>
 * 第二段用模组自己的伤害类型 {@code alchemy:double_strike}，它在数据包里被登记进
 * {@code minecraft:bypasses_cooldown} 标签。原版 {@code LivingEntity#hurt} 的核心判断是
 * <pre>{@code
 * if (invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) { ...只按增量结算... }
 * else { lastHurt = amount; invulnerableTime = 20; actuallyHurt(source, amount); }
 * }</pre>
 * 普通攻击在第一刀之后会把 {@code invulnerableTime} 置为 20，第二刀会被吃掉；而带上这个标签后走的是
 * else 分支，{@code lastHurt} 与 {@code invulnerableTime} 直接重写、伤害照常全额结算。
 * <p>
 * 因此第二段完全不需要等无敌帧结束，也没有任何 Mixin 或对 {@code invulnerableTime} 的直接写入。
 * <p>
 * 该伤害类型被强制归属于玩家（{@code new DamageSource(holder, player, player)}），所以击杀归属、
 * 死亡信息、仇恨都会记到玩家头上；同时它的 {@code exhaustion} 为 0，第二段不会额外消耗饥饿值。
 * <p>
 * 结算时机放在本 tick 的 {@link TickEvent.ServerTickEvent}（END 阶段）而不是直接回调：此时原版的
 * {@code hurt} 已经完整走完，不会在伤害计算中途重入。同一 tick 内落地，延迟可以忽略。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DoubleStrikeHandler
{
    /** 本 tick 待结算的第二段攻击。 */
    private static final List<PendingStrike> PENDING = new ArrayList<>();

    private DoubleStrikeHandler()
    {
    }

    /**
     * 第一刀命中时登记第二段。
     * <p>
     * 这里取的是 {@link LivingHurtEvent} 的 {@code amount}：它是护甲、附魔减伤、伤害吸收生效
     * <em>之前</em> 的数值（但已扣过盾牌格挡）。第二段会作为一个独立伤害再走一遍完整的受击结算，
     * 所以护甲、抗性、保护mini 都会照常生效——两段吃同样的减免，合起来才稳定是 1.5 倍。
     * 若这里改用护甲之后的值，第二段就会被护甲削两遍。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onLivingHurt(final LivingHurtEvent event)
    {
        // 不要对自己打出的第二段再触发一次，否则会无限连锁
        final DamageSource source = event.getSource();
        if (source.is(ModDamageTypes.DOUBLE_STRIKE))
        {
            return;
        }

        // 只处理「玩家用近战武器直接造成」的伤害：直接来源必须就是玩家本人，
        // 这样投掷物（箭、三叉戟）不会误触发。
        if (!(source.getDirectEntity() instanceof Player player)
                || !(source.getEntity() instanceof Player))
        {
            return;
        }

        final LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level))
        {
            return;
        }

        if (!ModEnchantments.DOUBLE_STRIKE.isPresent())
        {
            return;
        }

        final ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty())
        {
            return;
        }

        final Enchantment doubleStrike = ModEnchantments.DOUBLE_STRIKE.get();
        if (weapon.getEnchantmentLevel(doubleStrike) <= 0)
        {
            return;
        }

        final float bonus = DoubleStrikeMath.bonusDamage(event.getAmount(), DoubleStrikeMath.BONUS_RATIO);
        if (bonus <= 0.0F)
        {
            return;
        }

        // 一次挥砍只追加一刀：横扫命中的其他目标发生在同一 tick 里，但主目标先被处理，
        // 因此这里挡掉的正是横扫的附带目标，耐久也只扣一次。
        final int now = level.getServer().getTickCount();
        if (isAlreadyScheduled(player.getUUID(), now))
        {
            return;
        }

        PENDING.add(new PendingStrike(level, new WeakReference<>(player), target.getId(), bonus, now));
    }

    @SubscribeEvent
    static void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty())
        {
            return;
        }

        final int now = event.getServer().getTickCount();
        final Iterator<PendingStrike> iterator = PENDING.iterator();

        while (iterator.hasNext())
        {
            final PendingStrike strike = iterator.next();
            if (strike.scheduledTick() > now)
            {
                continue;
            }

            iterator.remove();
            applySecondHit(strike);
        }
    }

    /** 服务器关闭时清空队列，避免把上一局的目标带进下一局。 */
    @SubscribeEvent
    static void onServerStopped(final ServerStoppedEvent event)
    {
        PENDING.clear();
    }

    /**
     * 真正落下第二段伤害，并在这里扣掉额外的耐久。
     * <p>
     * 目标可能在这一 tick 内已经死亡、被移除或换了维度，这些情况都静默丢弃、也不扣耐久——
     * 耐久是「换来第二刀」的代价，没打出去就不该收费。
     */
    private static void applySecondHit(final PendingStrike strike)
    {
        if (!(strike.level().getEntity(strike.targetId()) instanceof LivingEntity target))
        {
            return;
        }

        if (!target.isAlive() || target.isRemoved() || target.level() != strike.level())
        {
            return;
        }

        final Player player = strike.player();
        if (player == null)
        {
            return;
        }

        final Optional<Holder.Reference<DamageType>> holder = strike.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(ModDamageTypes.DOUBLE_STRIKE);
        if (holder.isEmpty())
        {
            return;
        }

        // 直接 / 间接来源都填玩家本人：击杀归属、死亡信息、仇恨全部记到这名玩家头上
        target.hurt(new DamageSource(holder.get(), player, player), strike.damage());

        consumeExtraDurability(player);
    }

    /**
     * 多消耗一倍耐久：原版挥剑本身已经扣了 1 点，这里再扣 1 点。
     * <p>
     * 与 {@code Player#attack} 末尾的处理保持一致：耐久耗尽时补发销毁事件并清空主手，
     * 否则物品会在手上「假活着」。
     */
    private static void consumeExtraDurability(final Player player)
    {
        final ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !weapon.isDamageableItem())
        {
            return;
        }

        final ItemStack copy = weapon.copy();
        weapon.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        if (weapon.isEmpty())
        {
            ForgeEventFactory.onPlayerDestroyItem(player, copy, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** 这名玩家是否已经在同一 tick 里排过一次第二段（用于把横扫的附带目标挡掉）。 */
    private static boolean isAlreadyScheduled(final UUID playerId, final int tick)
    {
        for (PendingStrike strike : PENDING)
        {
            final Player scheduled = strike.player();
            if (strike.scheduledTick() == tick && scheduled != null && scheduled.getUUID().equals(playerId))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * 一笔待结算的第二段攻击。
     * <p>
     * 目标只存实体 id、玩家只存弱引用：队列活不过一个 tick，但两头都不该被队列拉住不释放。
     * 玩家用弱引用而不是 id 查表，是因为 {@code GameTestHelper#makeMockPlayer} 造出来的假玩家
     * 根本没进 {@code ServerLevel} 的实体表，按 id 找不到。
     */
    private record PendingStrike(ServerLevel level, WeakReference<Player> playerRef, int targetId,
                                 float damage, int scheduledTick)
    {
        /** 取出仍在线的玩家；已被回收则返回 {@code null}。 */
        Player player()
        {
            return playerRef.get();
        }
    }
}
