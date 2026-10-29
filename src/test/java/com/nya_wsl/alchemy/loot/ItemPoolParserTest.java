package com.nya_wsl.alchemy.loot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 配置条目 {@code "minecraft:raw_iron#20"} 的解析。
 * <p>
 * 单独抽成一个纯解析函数，是为了能在不启动游戏、不初始化注册表的情况下测试——
 * ForgeConfigSpec 的 validator 里不能查注册表，这里也刻意不查。
 */
class ItemPoolParserTest
{
    @Test
    @DisplayName("正常条目解析出 ID 与权重")
    void parsesIdAndWeight()
    {
        ItemPoolParser.Entry entry = ItemPoolParser.parseWeighted("minecraft:raw_iron#20");

        assertEquals(ResourceLocation.parse("minecraft:raw_iron"), entry.id());
        assertEquals(20, entry.weight());
    }

    @Test
    @DisplayName("省略权重时用默认权重")
    void omittedWeightUsesDefault()
    {
        ItemPoolParser.Entry entry = ItemPoolParser.parseWeighted("minecraft:diamond");

        assertEquals(ResourceLocation.parse("minecraft:diamond"), entry.id());
        assertEquals(ItemPoolParser.DEFAULT_WEIGHT, entry.weight());
    }

    @Test
    @DisplayName("前后空白会被裁掉")
    void trimsWhitespace()
    {
        ItemPoolParser.Entry entry = ItemPoolParser.parseWeighted("  minecraft:coal#30  ");

        assertEquals(ResourceLocation.parse("minecraft:coal"), entry.id());
        assertEquals(30, entry.weight());
    }

    @Test
    @DisplayName("权重非数字 / 非正数 / 空 ID 都返回 null，而不是静默取默认值")
    void rejectsBadWeight()
    {
        assertNull(ItemPoolParser.parseWeighted("minecraft:coal#abc"));
        assertNull(ItemPoolParser.parseWeighted("minecraft:coal#0"));
        assertNull(ItemPoolParser.parseWeighted("minecraft:coal#-5"));
        assertNull(ItemPoolParser.parseWeighted("#10"));
        assertNull(ItemPoolParser.parseWeighted(""));
        assertNull(ItemPoolParser.parseWeighted("   "));
        assertNull(ItemPoolParser.parseWeighted(null));
    }

    @Test
    @DisplayName("非法资源位置返回 null")
    void rejectsBadResourceLocation()
    {
        assertNull(ItemPoolParser.parseWeighted("not a valid id"));
        assertNull(ItemPoolParser.parseWeighted("minecraft:"));
    }

    @Test
    @DisplayName("宝石条目只有 ID，没有权重")
    void parsesPlainId()
    {
        assertEquals(ResourceLocation.parse("alchemy:ruby"), ItemPoolParser.parseId("alchemy:ruby"));
        assertEquals(ResourceLocation.parse("alchemy:ruby"), ItemPoolParser.parseId("  alchemy:ruby "));
        assertNull(ItemPoolParser.parseId(""));
        assertNull(ItemPoolParser.parseId(null));
        assertNull(ItemPoolParser.parseId("bad id"));
    }

    @Test
    @DisplayName("ID 解析为小写规范形式")
    void normalizesNamespaceCase()
    {
        ItemPoolParser.Entry entry = ItemPoolParser.parseWeighted("Minecraft:Raw_Iron#5");

        assertEquals("minecraft", entry.id().getNamespace());
        assertEquals(5, entry.weight());
    }
}
