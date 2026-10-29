package com.nya_wsl.alchemy;

import com.mojang.logging.LogUtils;
import com.nya_wsl.alchemy.enchantment.ModEnchantments;
import com.nya_wsl.alchemy.item.ModItems;
import com.nya_wsl.alchemy.loot.ModLootModifiers;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 点石成金：挖掘石头 / 圆石 / 下界岩时，有概率把掉落物换成原矿物品，还有小概率额外掉宝石。
 */
@Mod(ModMain.MODID)
public final class ModMain
{
    /** 模组 ID，必须与 META-INF/mods.toml 中的 modId 一致。 */
    public static final String MODID = "alchemy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModMain(final FMLJavaModLoadingContext context)
    {
        final IEventBus modEventBus = context.getModEventBus();

        // 注册附魔
        ModEnchantments.register(modEventBus);

        // 注册物品（6 种宝石）
        ModItems.register(modEventBus);

        // 注册全局掉落修饰器（石头 -> 原矿物品）
        ModLootModifiers.register(modEventBus);

        // 把宝石放进创造模式物品栏的「原材料」页，便于测试与查看
        modEventBus.addListener(this::addCreativeTabContents);

        // 注册通用配置（alchemy-common.toml）
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void addCreativeTabContents(final BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() != CreativeModeTabs.INGREDIENTS)
        {
            return;
        }

        event.accept(ModItems.RUBY);
        event.accept(ModItems.AMBER);
        event.accept(ModItems.TOPAZ);
        event.accept(ModItems.JADE);
        event.accept(ModItems.SAPPHIRE);
        event.accept(ModItems.AMETHYST);
    }
}
