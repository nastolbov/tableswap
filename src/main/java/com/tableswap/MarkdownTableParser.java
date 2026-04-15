package com.tableswap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Парсер таблиц из Markdown и из «сырого» копи-пейста.
 *
 * <p>Поддерживаются два формата:
 * <ol>
 *   <li>Markdown (GitHub Flavored) с разделителем {@code |---|---|};</li>
 *   <li>TSV-подобный — строки, в которых ячейки разделены табуляцией
 *       (именно в таком виде Word/Excel/Google Docs кладут таблицу в буфер
 *       обмена).</li>
 * </ol>
 *
 * <p>Если перед таблицей идёт одиночная строка, не похожая на табличную
 * (например, «Таблица 80 — Описание колонок…»), она сохраняется как
 * {@link MarkdownTable#caption() подпись} и будет выведена отдельным абзацем
 * над таблицей в {@code .docx}.
 */
public final class MarkdownTableParser {

    private MarkdownTableParser() {
    }

    public static MarkdownTable parse(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Пустой ввод — вставьте таблицу.");
        }

        List<String> lines = new ArrayList<>();
        for (String raw : source.split("\\R", -1)) {
            lines.add(raw.stripTrailing());
        }

        MarkdownTable md = tryParseMarkdown(lines);
        if (md != null) {
            return md;
        }
        MarkdownTable tsv = tryParseTsv(lines);
        if (tsv != null) {
            return tsv;
        }
        throw new IllegalArgumentException(
                "Не удалось распознать таблицу.\n"
                        + "Поддерживаются:\n"
                        + "  • Markdown с разделителем |---|---|\n"
                        + "  • Таблица, скопированная из Word/Excel (ячейки через табуляцию).");
    }

    // --- Markdown --------------------------------------------------------

    private static MarkdownTable tryParseMarkdown(List<String> lines) {
        int separatorIdx = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (isSeparatorLine(lines.get(i)) && isPipeTableLine(lines.get(i - 1))) {
                separatorIdx = i;
                break;
            }
        }
        if (separatorIdx == -1) {
            return null;
        }

        List<String> header = splitPipeRow(lines.get(separatorIdx - 1));
        int cols = header.size();

        List<List<String>> rows = new ArrayList<>();
        for (int i = separatorIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                if (!rows.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!isPipeTableLine(line)) {
                break;
            }
            List<String> row = new ArrayList<>(splitPipeRow(line));
            normaliseRow(row, cols);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return null;
        }

        String caption = extractCaption(lines, separatorIdx - 1);
        return new MarkdownTable(caption, header, rows);
    }

    private static boolean isPipeTableLine(String line) {
        return line != null && !line.isEmpty() && line.contains("|");
    }

    private static boolean isSeparatorLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        String trimmed = line.strip();
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
            if (p.isEmpty() || !p.matches(":?-{3,}:?")) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitPipeRow(String line) {
        String s = line.strip();
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

    // --- TSV -------------------------------------------------------------

    private static MarkdownTable tryParseTsv(List<String> lines) {
        int startIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("\t")) {
                startIdx = i;
                break;
            }
        }
        if (startIdx == -1) {
            return null;
        }

        List<List<String>> all = new ArrayList<>();
        for (int i = startIdx; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                break;
            }
            if (!line.contains("\t")) {
                break;
            }
            String[] parts = line.split("\\t", -1);
            List<String> cells = new ArrayList<>(parts.length);
            for (String p : parts) {
                cells.add(p.strip());
            }
            all.add(cells);
        }

        if (all.size() < 2) {
            return null;
        }

        List<String> header = all.get(0);
        int cols = header.size();
        List<List<String>> rows = new ArrayList<>(all.size() - 1);
        for (int i = 1; i < all.size(); i++) {
            List<String> row = new ArrayList<>(all.get(i));
            normaliseRow(row, cols);
            rows.add(row);
        }

        String caption = extractCaption(lines, startIdx);
        return new MarkdownTable(caption, header, rows);
    }

    // --- helpers ---------------------------------------------------------

    private static void normaliseRow(List<String> row, int cols) {
        while (row.size() < cols) {
            row.add("");
        }
        while (row.size() > cols) {
            row.remove(row.size() - 1);
        }
    }

    /**
     * Возвращает одиночную «подпись» над таблицей: ближайшую непустую строку
     * выше первой строки таблицы, которую нельзя принять за табличную.
     * Убирает ведущие символы markdown-заголовков ({@code #}).
     */
    private static String extractCaption(List<String> lines, int tableStartIdx) {
        for (int i = tableStartIdx - 1; i >= 0; i--) {
            String c = lines.get(i).strip();
            if (c.isEmpty()) {
                continue;
            }
            if (c.contains("\t") || c.contains("|")) {
                return null;
            }
            while (c.startsWith("#")) {
                c = c.substring(1);
            }
            c = c.strip();
            return c.isEmpty() ? null : c;
        }
        return null;
    }
}
