package org.kwcrawler;

import java.util.HashMap;
import java.util.Map;

public class ControlDigit {

    private final static int[] weights = { 1, 3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7 };
    private final static Map<Character, Integer> decodingValues = new HashMap<>();

    static {
        decodingValues.put('0', 0);
        decodingValues.put('1', 1);
        decodingValues.put('2', 2);
        decodingValues.put('3', 3);
        decodingValues.put('4', 4);
        decodingValues.put('5', 5);
        decodingValues.put('6', 6);
        decodingValues.put('7', 7);
        decodingValues.put('8', 8);
        decodingValues.put('9', 9);
        decodingValues.put('X', 10);
        decodingValues.put('A', 11);
        decodingValues.put('B', 12);
        decodingValues.put('C', 13);
        decodingValues.put('D', 14);
        decodingValues.put('E', 15);
        decodingValues.put('F', 16);
        decodingValues.put('G', 17);
        decodingValues.put('H', 18);
        decodingValues.put('I', 19);
        decodingValues.put('J', 20);
        decodingValues.put('K', 21);
        decodingValues.put('L', 22);
        decodingValues.put('M', 23);
        decodingValues.put('N', 24);
        decodingValues.put('O', 25);
        decodingValues.put('P', 26);
        decodingValues.put('R', 27);
        decodingValues.put('S', 28);
        decodingValues.put('T', 29);
        decodingValues.put('U', 30);
        decodingValues.put('W', 31);
        decodingValues.put('Y', 32);
        decodingValues.put('Z', 33);
    }

    public static byte calculate(String courtCode, String ledgerNumber) {
        int result = 0;
        for (int i = 0; i < courtCode.length(); i++) {
            char ch = courtCode.charAt(i);
            result += decodingValues.get(ch) * weights[i];
        }

        for (int i = 0; i < ledgerNumber.length(); i++) {
            char ch = ledgerNumber.charAt(i);
            result += decodingValues.get(ch) * weights[i+4];
        }

        return (byte)(result % 10);
    }

    public static byte calculate(CourtCode courtCode, Integer ledgerNumber) {
        return calculate(courtCode.getCode(), String.format("%08d", ledgerNumber));
    }
}
