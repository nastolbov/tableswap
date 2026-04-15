package com.tableswap;

import java.util.regex.Pattern;

/**
 * Определяет тип содержимого ячейки, чтобы выбрать выравнивание:
 * числа и даты — по правому краю, обычный текст — по ширине.
 */
public final class CellClassifier {

    public enum CellType {
        NUMBER,
        DATE,
        TEXT
    }

    // 1 234,56 ; 1234.56 ; -42 ; 3,14% ; 1 000 000
    private static final Pattern NUMBER = Pattern.compile(
            "^[+\\-]?\\d{1,3}(?:[ \\u00A0]\\d{3})*(?:[.,]\\d+)?\\s?%?$"
            + "|^[+\\-]?\\d+(?:[.,]\\d+)?\\s?%?$");

    // 2024-01-31, 31.01.2024, 31/01/2024, 31-01-2024, 2024/01/31
    private static final Pattern DATE_NUMERIC = Pattern.compile(
            "^\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}$"
            + "|^\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}$");

    // 31 января 2024, 31 янв. 2024, 1 мая 2025 г.
    private static final Pattern DATE_RU_WORD = Pattern.compile(
            "^\\d{1,2}\\s+\\p{L}+\\.?\\s+\\d{4}(?:\\s*г\\.?)?$");

    private CellClassifier() {
    }

    public static CellType classify(String value) {
        if (value == null) {
            return CellType.TEXT;
        }
        String v = value.strip();
        if (v.isEmpty()) {
            return CellType.TEXT;
        }
        if (NUMBER.matcher(v).matches()) {
            return CellType.NUMBER;
        }
        if (DATE_NUMERIC.matcher(v).matches() || DATE_RU_WORD.matcher(v).matches()) {
            return CellType.DATE;
        }
        return CellType.TEXT;
    }
}
