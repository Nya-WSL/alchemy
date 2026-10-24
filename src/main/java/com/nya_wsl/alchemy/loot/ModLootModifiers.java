package com.nya_wsl.alchemy.loot;

import com.mojang.serialization.Codec;
import com.nya_wsl.alchemy.ModMain;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers
{
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ModMain.MODID);

    public static final RegistryObject<Codec<StoneTransmutationModifier>> STONE_TRANSMUTATION =
            SERIALIZERS.register("stone_transmutation", () -> StoneTransmutationModifier.CODEC);

    private ModLootModifiers()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        SERIALIZERS.register(modEventBus);
    }
}
