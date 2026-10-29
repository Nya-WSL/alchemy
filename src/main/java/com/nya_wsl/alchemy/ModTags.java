package com.nya_wsl.alchemy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 模组使用的方块标签。数据包可通过覆盖 {@code data/alchemy/tags/blocks/*.json} 增删可转化的方块。
 * <p>
 * 两张标签按<b>方块家族</b>划分，而不是按维度——决定走哪张物品表只看方块本身，
 * 所以主世界里的下界岩、或者模组加进标签的方块都能按预期工作。
 */
public final class ModTags
{
    private ModTags()
    {
    }

    public static final class Blocks
    {
        /** 走主世界物品表的方块（默认：石头、圆石、深板岩、深板岩圆石）。 */
        public static final TagKey<Block> OVERWORLD_TRANSMUTABLE =
                TagKey.create(Registries.BLOCK,
                        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "overworld_transmutable"));

        /** 走下界物品表的方块（默认：下界岩）。 */
        public static final TagKey<Block> NETHER_TRANSMUTABLE =
                TagKey.create(Registries.BLOCK,
                        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "nether_transmutable"));

        private Blocks()
        {
        }
    }
}
