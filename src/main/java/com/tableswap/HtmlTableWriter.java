package com.tableswap;

import java.util.List;

/**
 * Рендерит {@link MarkdownTable} в HTML, который можно положить в буфер
 * обмена и вставить в Word: сохраняются шрифт, размер, межстрочный,
 * выравнивания, границы и пропорциональные ширины колонок.
 *
 * <p>Word на macOS и Windows понимает HTML-таблицу из буфера и вставляет
 * её как нативную Word-таблицу с применёнными стилями.
 */
public final class HtmlTableWriter {

    private static final String FONT = "'Times New Roman', Times, serif";
    private static final String FONT_SIZE = "12pt";
    private static final String LINE_HEIGHT = "1.5";
    private static final String BORDER = "1px solid #000";
    private static final String CELL_PADDING = "2pt 6pt";

    private HtmlTableWriter() {
    }

    public static String toHtml(MarkdownTable table) {
        int cols = table.columnCount();
        int[] weights = computeWeights(table);
        int weightSum = 0;
        for (int w : weights) {
            weightSum += w;
        }

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<html><head><meta charset=\"UTF-8\"></head><body>");

        if (table.caption() != null && !table.caption().isBlank()) {
            sb.append("<p style=\"font-family:").append(FONT)
                    .append(";font-size:").append(FONT_SIZE)
                    .append(";line-height:").append(LINE_HEIGHT)
                    .append(";font-weight:bold;margin:0 0 6pt 0\">")
                    .append(escape(table.caption()))
                    .append("</p>");
        }

        sb.append("<table style=\"border-collapse:collapse;width:100%;font-family:")
                .append(FONT)
                .append(";font-size:").append(FONT_SIZE)
                .append(";line-height:").append(LINE_HEIGHT)
                .append("\">");

        // Сетка ширины колонок — Word её уважает.
        sb.append("<colgroup>");
        for (int c = 0; c < cols; c++) {
            double pct = weights[c] * 100.0 / weightSum;
            sb.append("<col style=\"width:")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", pct))
                    .append("%\"/>");
        }
        sb.append("</colgroup>");

        // Заголовок.
        sb.append("<thead><tr>");
        for (String h : table.header()) {
            sb.append("<th style=\"border:").append(BORDER)
                    .append(";padding:").append(CELL_PADDING)
                    .append(";text-align:center;vertical-align:top;font-weight:bold\">")
                    .append(escapeMultiline(h))
                    .append("</th>");
        }
        sb.append("</tr></thead>");

        // Данные.
        sb.append("<tbody>");
        for (List<String> row : table.rows()) {
            sb.append("<tr>");
            for (int c = 0; c < cols; c++) {
                String value = c < row.size() ? row.get(c) : "";
                String align = switch (CellClassifier.classify(value)) {
                    case NUMBER, DATE -> "right";
                    case TEXT -> "justify";
                };
                sb.append("<td style=\"border:").append(BORDER)
                        .append(";padding:").append(CELL_PADDING)
                        .append(";text-align:").append(align)
                        .append(";vertical-align:top\">")
                        .append(escapeMultiline(value))
                        .append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody>");

        sb.append("</table></body></html>");
        return sb.toString();
    }

    /**
     * Plain-text представление таблицы (TSV) — запасной вариант для
     * приложений, которые не умеют читать HTML из буфера обмена.
     */
    public static String toPlainText(MarkdownTable table) {
        StringBuilder sb = new StringBuilder();
        if (table.caption() != null && !table.caption().isBlank()) {
            sb.append(table.caption()).append('\n');
        }
        appendRow(sb, table.header());
        for (List<String> row : table.rows()) {
            appendRow(sb, row);
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                sb.append('\t');
            }
            sb.append(row.get(i).replace('\n', ' '));
        }
        sb.append('\n');
    }

    private static int[] computeWeights(MarkdownTable table) {
        int cols = table.columnCount();
        int[] lens = new int[cols];
        for (int c = 0; c < cols; c++) {
            lens[c] = longestLine(table.header().get(c));
        }
        for (List<String> row : table.rows()) {
            for (int c = 0; c < cols && c < row.size(); c++) {
                lens[c] = Math.max(lens[c], longestLine(row.get(c)));
            }
        }
        int[] weights = new int[cols];
        for (int c = 0; c < cols; c++) {
            weights[c] = Math.max(3, Math.min(lens[c], 30));
        }
        return weights;
    }

    private static int longestLine(String s) {
        if (s == null || s.isEmpty()) {
            return 1;
        }
        int max = 0;
        for (String line : s.split("\\R")) {
            if (line.length() > max) {
                max = line.length();
            }
        }
        return Math.max(max, 1);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String escapeMultiline(String s) {
        return escape(s).replace("\n", "<br/>");
    }
}
