package org.kwcrawler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class CourtCodeValidator {
    private static final List<String> courtCodes;

    static {
        try (var inputStream = CourtCodeValidator.class.getClassLoader().getResourceAsStream("court-codes.txt");
            var scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            courtCodes = scanner.useDelimiter("\\A").next().lines().collect(Collectors.toList());
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException("Failed to read departments file", e);
        }
    }

    public static boolean isValidCourtCode(String courtCode) {
        return courtCodes.contains(courtCode);
    }
}