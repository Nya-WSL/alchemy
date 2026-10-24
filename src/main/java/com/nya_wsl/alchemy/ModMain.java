package com.nya_wsl.alchemy;

import com.mojang.logging.LogUtils;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import com.nya_wsl.alchemy.loot.ModLootModifiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Turn Stone Into Gold：挖掘石头 / 圆石时，有概率把掉落物变成矿物。
 */
@Mod(ModMain.MODID)
public final class ModMain
{
    /** 模组 ID，必须与 META-INF/mods.toml 中的 modId 一致。 */
    public static final String MODID = "alchemy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModMain(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // 注册附魔
        ModEnchantments.register(modEventBus);

        // 注册全局掉落修饰器（石头 -> 矿物）
        ModLootModifiers.register(modEventBus);

        // 注册通用配置（alchemy-common.toml）
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
