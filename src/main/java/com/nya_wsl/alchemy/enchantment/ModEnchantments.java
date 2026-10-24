package com.nya_wsl.alchemy.enchantment;

import com.nya_wsl.alchemy.ModMain;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments
{
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, ModMain.MODID);

    /** 点石成金：挖掘石头 / 圆石时，有概率把掉落物变成矿物。 */
    public static final RegistryObject<Enchantment> TURN_STONE_INTO_GOLD =
            ENCHANTMENTS.register("turn_stone_into_gold", TransmutationEnchantment::new);

    private ModEnchantments()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        ENCHANTMENTS.register(modEventBus);
    }
}
