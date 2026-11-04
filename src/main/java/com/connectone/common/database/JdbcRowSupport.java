package com.connectone.common.database;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JdbcRowSupport {

    private JdbcRowSupport() {
    }

    public static List<Map<String, Object>> camelCaseRows(List<Map<String, Object>> rows) {
        return rows.stream().map(JdbcRowSupport::camelCaseRow).toList();
    }

    public static Map<String, Object> camelCaseRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(camelCase(key), value));
        return normalized;
    }

    private static String camelCase(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean uppercaseNext = false;
        for (char character : normalized.toCharArray()) {
            if (character == '_') {
                uppercaseNext = true;
            } else {
                result.append(uppercaseNext ? Character.toUpperCase(character) : character);
                uppercaseNext = false;
            }
        }
        return result.toString();
    }
}
