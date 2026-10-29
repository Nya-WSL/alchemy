package com.nya_wsl.alchemy.combat;

import com.nya_wsl.alchemy.Config;
import com.nya_wsl.alchemy.ModMain;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 让「保护mini」在伤害结算的最后一步固定减伤。
 * <p>
 * 用 {@link LivingDamageEvent} 而不是 LivingHurtEvent：前者的伤害值已经过护甲、保护类附魔、
 * 抗性药水和伤害吸收，减伤数值才是玩家实际承受的「固定减伤」；LivingHurtEvent 阶段扣减会被
 * 后续的百分比减免再次打折。
 * <p>
 * 减免到 0 时取消事件，血量不变、不扣饥饿、不计入战斗记录。
 * 注意：无敌帧和受击动画是在 {@code LivingEntity#hurt} 里、{@code actuallyHurt} 之前就设置好的，
 * 所以这一击仍然会进入 10 tick 的无敌时间——这是固定减伤的固有结果，并非本模组的额外行为。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MiniProtectionHandler
{
    /** 参与结算的四个护甲位。 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private MiniProtectionHandler()
    {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onLivingDamage(final LivingDamageEvent event)
    {
        final LivingEntity entity = event.getEntity();

        // 与原版保护类附魔一致：无视无敌 / 无视附魔的伤害不吃这份减伤
        final DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS))
        {
            return;
        }

        // 可配置为只对玩家生效（默认 true）
        if (Config.miniProtectionPlayersOnly && !(entity instanceof Player))
        {
            return;
        }

        final int totalLevels = countLevels(entity);
        if (totalLevels <= 0)
        {
            return;
        }

        final float reduction = MiniProtectionMath.reductionFor(
                totalLevels, Config.miniProtectionReductionPerLevel);
        if (reduction <= 0.0F)
        {
            return;
        }

        final float reduced = MiniProtectionMath.applyReduction(event.getAmount(), reduction);
        if (reduced <= 0.0F)
        {
            // 全部挡下：血量不变、不扣饥饿、不记战斗数据
            event.setAmount(0.0F);
            event.setCanceled(true);
            return;
        }

        event.setAmount(reduced);
    }

    /** 把四件护甲上的保护mini等级相加（与「取最大值」的原版保护不同）。 */
    private static int countLevels(final LivingEntity entity)
    {
        if (!ModEnchantments.MINI_PROTECTION.isPresent())
        {
            return 0;
        }

        final Enchantment miniProtection = ModEnchantments.MINI_PROTECTION.get();
        int total = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS)
        {
            final ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }

            total += stack.getEnchantmentLevel(miniProtection);
        }

        return total;
    }
}
