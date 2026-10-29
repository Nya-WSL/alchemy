package com.nya_wsl.alchemy.loot;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * 物品表配置条目的纯解析层：{@code "namespace:path#weight"}。
 * <p>
 * 刻意<b>不查注册表</b>——ForgeConfigSpec 的 validator 在配置加载期运行，那时注册表可能还没填充。
 * 解析只负责把字符串变成 {@link ResourceLocation}，真正的物品解析放到
 * {@code Config.rebuildItemPool()} 里注册表就绪之后做。
 * <p>
 * 坏条目一律返回 {@code null}，由上层逐条 warn 出具体 ID（设计文档第 9 节：不静默回退）。
 */
public final class ItemPoolParser
{
    /** 条目省略权重时使用的默认值。 */
    public static final int DEFAULT_WEIGHT = 10;

    private ItemPoolParser()
    {
    }

    /**
     * 解析带权重的条目，如 {@code "minecraft:raw_iron#20"}。
     *
     * @param raw 原始配置字符串
     * @return 解析结果；格式非法时返回 {@code null}
     */
    public static Entry parseWeighted(final String raw)
    {
        if (raw == null)
        {
            return null;
        }

        String value = raw.trim();
        if (value.isEmpty())
        {
            return null;
        }

        int weight = DEFAULT_WEIGHT;
        // lastIndexOf：资源位置本身不含 '#'，用 last 更宽容
        final int hash = value.lastIndexOf('#');
        if (hash >= 0)
        {
            final String weightText = value.substring(hash + 1).trim();
            try
            {
                weight = Integer.parseInt(weightText);
            }
            catch (final NumberFormatException e)
            {
                return null;
            }

            if (weight <= 0)
            {
                return null;
            }

            value = value.substring(0, hash).trim();
        }

        final ResourceLocation id = parseId(value);
        return id == null ? null : new Entry(id, weight);
    }

    /**
     * 解析纯 ID 条目，如 {@code "alchemy:ruby"}。
     * <p>
     * 这里<b>自己做严格校验</b>而不是直接交给 {@code ResourceLocation.tryParse}：
     * 原版对空 path（{@code "minecraft:"}）是放行的，还会因为大小写规则给出反直觉的结果。
     * 配置里出现这种残条目应当被明确报出来，而不是变成「名字为空的物品」。
     * 输入统一转小写——物品 ID 本身全是小写，容忍手写大写不会造成歧义。
     *
     * @param raw 原始配置字符串
     * @return 解析结果；格式非法时返回 {@code null}
     */
    public static ResourceLocation parseId(final String raw)
    {
        if (raw == null)
        {
            return null;
        }

        final String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty())
        {
            return null;
        }

        final int colon = value.indexOf(':');
        // 必须恰好一个冒号；允许省略命名空间时按 minecraft 处理没意义（配置里应当写全）
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1)
        {
            return null;
        }

        final String namespace = value.substring(0, colon);
        final String path = value.substring(colon + 1);

        if (!isValidNamespace(namespace) || !isValidPath(path))
        {
            return null;
        }

        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** 命名空间字符集：{@code [a-z0-9_.-]}。 */
    private static boolean isValidNamespace(final String namespace)
    {
        for (int i = 0; i < namespace.length(); i++)
        {
            final char c = namespace.charAt(i);
            final boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            if (!ok)
            {
                return false;
            }
        }

        return true;
    }

    /** 路径字符集：{@code [a-z0-9_./-]}。 */
    private static boolean isValidPath(final String path)
    {
        for (int i = 0; i < path.length(); i++)
        {
            final char c = path.charAt(i);
            final boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-' || c == '/';
            if (!ok)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * 一条物品权重配置。
     *
     * @param id     物品 ID
     * @param weight 权重，恒大于 0
     */
    public record Entry(ResourceLocation id, int weight)
    {
    }
}
