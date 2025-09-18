package org.kwcrawler.parser;


public class TextUtils {
    public static String toSuperScript(String text) {
        return text
                .replace("0", "⁰")
                .replace("1", "¹")
                .replace("2", "²")
                .replace("3", "³")
                .replace("4", "⁴")
                .replace("5", "⁵")
                .replace("6", "⁶")
                .replace("7", "⁷")
                .replace("8", "⁸")
                .replace("9", "⁹")
                .replace(",", "˒");
    }

    public static String toSubScript(String text) {
        return text
                .replace("0", "₀")
                .replace("1", "₁")
                .replace("2", "₂")
                .replace("3", "₃")
                .replace("4", "₄")
                .replace("5", "₅")
                .replace("6", "₆")
                .replace("7", "₇")
                .replace("8", "₈")
                .replace("9", "₉")
                .replace(",", " ");
    }
}
