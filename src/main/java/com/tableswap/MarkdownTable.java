package com.tableswap;

import java.util.List;

/**
 * Представление распарсенной таблицы:
 * необязательная подпись (например «Таблица 80 — …»),
 * строка заголовка и строки данных.
 */
public final class MarkdownTable {
    private final String caption;
    private final List<String> header;
    private final List<List<String>> rows;

    public MarkdownTable(String caption, List<String> header, List<List<String>> rows) {
        this.caption = caption;
        this.header = List.copyOf(header);
        this.rows = List.copyOf(rows);
    }

    public MarkdownTable(List<String> header, List<List<String>> rows) {
        this(null, header, rows);
    }

    public String caption() {
        return caption;
    }

    public List<String> header() {
        return header;
    }

    public List<List<String>> rows() {
        return rows;
    }

    public int columnCount() {
        return header.size();
    }
}
