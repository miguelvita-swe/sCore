package br.com.skyy.core.utils;

import br.com.skyy.core.version.ServerVersion;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colorize strings with & codes and hex colors for 1.16+.
 * Supported hex formats: {#RRGGBB} and &#RRGGBB
 */
public final class ColorUtil {

    private static final Pattern HEX_CURLY  = Pattern.compile("\\{#([A-Fa-f0-9]{6})}");
    private static final Pattern HEX_AMP    = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {}

    public static String colorize(String text) {
        if (text == null) return "";
        if (ServerVersion.getCurrent().hasHexColors()) {
            text = applyHex(text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> colorize(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) result.add(colorize(s));
        return result;
    }

    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static String applyHex(String text) {
        // Handle {#RRGGBB}
        text = applyPattern(text, HEX_CURLY);
        // Handle &#RRGGBB
        text = applyPattern(text, HEX_AMP);
        return text;
    }

    private static String applyPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00A7').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
