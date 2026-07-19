package com.unisubmit.util;

/**
 * Minimal CSV field escaping shared by every CSV export path (marks export, import
 * credentials download). Quotes a field only when it contains a comma, a double quote,
 * or a newline, doubling any embedded quotes — RFC-4180 style.
 */
public final class CsvUtil {

    private CsvUtil() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
