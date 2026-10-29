package com.nya_wsl.alchemy.item;

import com.nya_wsl.alchemy.ModMain;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组物品。目前只有 6 种宝石。
 * <p>
 * 宝石是「点石成金」LV3+ 的稀有额外掉落（设计文档第 10 节），本次只做成可掉落的普通物品；
 * 它们的属性加成在第 3 节，属于后续工作，这里先不挂任何 buff。
 * <p>
 * 命名上刻意避开歧义：{@code alchemy:jade} 中文名「翡翠」，
 * 与主世界矿表里的原版 {@code minecraft:emerald}「绿宝石」是两个不同的东西。
 */
public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModMain.MODID);

    /** 红宝石：后续 +2% 伤害增幅。 */
    public static final RegistryObject<Item> RUBY = ITEMS.register("ruby",
            () -> new Item(new Item.Properties()));

    /** 橙宝石：后续 +2% 攻速。 */
    public static final RegistryObject<Item> AMBER = ITEMS.register("amber",
            () -> new Item(new Item.Properties()));

    /** 黄宝石：后续 +1% 伤害减免。 */
    public static final RegistryObject<Item> TOPAZ = ITEMS.register("topaz",
            () -> new Item(new Item.Properties()));

    /** 翡翠：后续 +1 幸运。与 minecraft:emerald（绿宝石）不同。 */
    public static final RegistryObject<Item> JADE = ITEMS.register("jade",
            () -> new Item(new Item.Properties()));

    /** 蓝宝石：后续 +5% 移速。 */
    public static final RegistryObject<Item> SAPPHIRE = ITEMS.register("sapphire",
            () -> new Item(new Item.Properties()));

    /** 紫宝石：后续 +0.1 心 / 5s 回复。 */
    public static final RegistryObject<Item> AMETHYST = ITEMS.register("amethyst",
            () -> new Item(new Item.Properties()));

    private ModItems()
    {
    }

    public static void register(final IEventBus modEventBus)
    {
        ITEMS.register(modEventBus);
    }
}
