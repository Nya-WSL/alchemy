package com.nya_wsl.alchemy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * 模组使用的伤害类型。1.20.1 的伤害类型由数据包注册，这里只保留注册键，
 * 实际内容见 data/alchemy/damage_type/*.json。
 */
public final class ModDamageTypes
{
    private ModDamageTypes()
    {
    }

    /**
     * 「双重打击」的第二段伤害。
     * <p>
     * 该类型被登记在 data/minecraft/tags/damage_type/bypasses_cooldown.json 里，
     * 因此可以无视受击者的无敌帧直接命中——这正是第二段攻击能落地的前提。
     * 同时它把玩家记为伤害来源，击杀判定与死亡信息才会归到该玩家名下。
     */
    public static final ResourceKey<DamageType> DOUBLE_STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "double_strike"));
}
