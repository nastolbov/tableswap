package com.tableswap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Простой парсер таблиц Markdown в формате GitHub Flavored Markdown:
 *
 * <pre>
 * | Заголовок 1 | Заголовок 2 |
 * |-------------|-------------|
 * | значение    | 42          |
 * </pre>
 *
 * Допускаются строки как с обрамляющими «|», так и без них.
 * Экранированные «\|» внутри ячеек сохраняются как символ «|».
 */
public final class MarkdownTableParser {

    private MarkdownTableParser() {
    }

    public static MarkdownTable parse(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Пустой ввод — вставьте таблицу Markdown.");
        }

        List<String> lines = new ArrayList<>();
        for (String raw : source.split("\\R", -1)) {
            lines.add(raw.strip());
        }

        int separatorIdx = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (isSeparatorLine(lines.get(i)) && isTableLine(lines.get(i - 1))) {
                separatorIdx = i;
                break;
            }
        }
        if (separatorIdx == -1) {
            throw new IllegalArgumentException(
                    "Не найдена таблица Markdown: отсутствует строка-разделитель вида |---|---|.");
        }

        List<String> header = splitRow(lines.get(separatorIdx - 1));
        int cols = header.size();

        List<List<String>> rows = new ArrayList<>();
        for (int i = separatorIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                if (!rows.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!isTableLine(line)) {
                break;
            }
            List<String> row = new ArrayList<>(splitRow(line));
            while (row.size() < cols) {
                row.add("");
            }
            if (row.size() > cols) {
                row = row.subList(0, cols);
            }
            rows.add(row);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("В таблице нет ни одной строки данных.");
        }
        return new MarkdownTable(header, rows);
    }

    private static boolean isTableLine(String line) {
        return line != null && !line.isEmpty() && line.contains("|");
    }

    private static boolean isSeparatorLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        String trimmed = line;
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return false;
        }
        String[] parts = trimmed.split("\\|", -1);
        if (parts.length == 0) {
            return false;
        }
        for (String part : parts) {
            String p = part.strip();
            if (p.isEmpty()) {
                return false;
            }
            if (!p.matches(":?-{3,}:?")) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitRow(String line) {
        // Снимаем один ведущий и один замыкающий «|», если они есть.
        String s = line;
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|") && !s.endsWith("\\|")) {
            s = s.substring(0, s.length() - 1);
        }

        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '|') {
                current.append('|');
                i++;
            } else if (c == '|') {
                cells.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().strip());
        return Arrays.asList(cells.toArray(new String[0]));
    }
}
