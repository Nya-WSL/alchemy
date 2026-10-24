package com.nya_wsl.alchemy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 模组使用的标签。数据包可通过覆盖 data/alchemy/tags/blocks/*.json 增删可转化的方块。
 */
public final class ModTags
{
    private ModTags()
    {
    }

    public static final class Blocks
    {
        /** 可被「点石成金」转化的方块（默认：石头、圆石、深板岩、深板岩圆石）。 */
        public static final TagKey<Block> TRANSMUTABLE =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "transmutable"));

        /** 属于深板岩家族的方块，转化时改用深板岩变种矿石。 */
        public static final TagKey<Block> DEEPSLATE_TRANSMUTABLE =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "deepslate_transmutable"));

        private Blocks()
        {
        }
    }
}
