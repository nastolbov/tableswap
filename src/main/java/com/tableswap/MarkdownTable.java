package com.tableswap;

import java.util.List;

/**
 * Представление распарсенной таблицы Markdown:
 * заголовок (одна строка) и строки данных.
 */
public final class MarkdownTable {
    private final List<String> header;
    private final List<List<String>> rows;

    public MarkdownTable(List<String> header, List<List<String>> rows) {
        this.header = List.copyOf(header);
        this.rows = List.copyOf(rows);
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
