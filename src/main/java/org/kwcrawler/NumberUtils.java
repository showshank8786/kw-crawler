package org.kwcrawler;


import com.github.chaosfirebolt.converter.RomanInteger;

public class NumberUtils {
    public static Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseRomanOrArabic(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            try {
                return RomanInteger.parse(value).getArabic();
            } catch (NumberFormatException f) {
                return null;
            }
        }
    }
}
