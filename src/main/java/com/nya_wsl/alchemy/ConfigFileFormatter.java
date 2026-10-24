package com.nya_wsl.alchemy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Forge 写配置文件时会把列表挤成一行（如 {@code ores = ["a", "b", ...]}），条目一多就没法编辑。
 * 这里在配置加载后把指定键的单行数组重排成每行一项，内容与语义完全不变。
 * <p>
 * 只在文本确实发生变化时写盘，避免与 Forge 的配置文件监听形成写盘循环。
 */
public final class ConfigFileFormatter
{
    private ConfigFileFormatter()
    {
    }

    /**
     * 重排配置文件里的单行数组。
     *
     * @return 是否真的改写了文件
     */
    public static boolean formatArrays(Path file, String... keys) throws IOException
    {
        if (!Files.isRegularFile(file))
        {
            return false;
        }

        String text = Files.readString(file, StandardCharsets.UTF_8);
        String formatted = formatArrays(text, keys);
        if (formatted.equals(text))
        {
            return false;
        }

        Files.writeString(file, formatted, StandardCharsets.UTF_8);
        return true;
    }

    static String formatArrays(String text, String... keys)
    {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        String result = text;
        for (String key : keys)
        {
            result = formatArray(result, key, newline);
        }
        return result;
    }

    private static String formatArray(String text, String key, String newline)
    {
        Pattern linePattern = Pattern.compile(
                "(?m)^([ \\t]*)" + Pattern.quote(key) + "[ \\t]*=[ \\t]*\\[([^\\]\\r\\n]*)\\]");
        Matcher lineMatcher = linePattern.matcher(text);
        if (!lineMatcher.find())
        {
            return text;
        }

        List<String> items = new ArrayList<>();
        Matcher itemMatcher = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(lineMatcher.group(2));
        while (itemMatcher.find())
        {
            items.add(itemMatcher.group(1));
        }

        if (items.isEmpty())
        {
            return text;
        }

        String indent = lineMatcher.group(1);
        StringBuilder builder = new StringBuilder();
        builder.append(indent).append(key).append(" = [");
        for (int i = 0; i < items.size(); i++)
        {
            builder.append(newline).append(indent).append('\t').append('"').append(items.get(i)).append('"');
            if (i < items.size() - 1)
            {
                builder.append(',');
            }
        }
        builder.append(newline).append(indent).append(']');

        return text.substring(0, lineMatcher.start()) + builder + text.substring(lineMatcher.end());
    }
}
