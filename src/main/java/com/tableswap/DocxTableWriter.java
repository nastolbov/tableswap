package com.tableswap;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.List;

import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

/**
 * Записывает {@link MarkdownTable} в .docx по заданному ТЗ:
 * Times New Roman 12, междустрочный 1.5, заголовки колонок — по центру,
 * числа и даты — по правому краю, текст — по ширине, обычная таблица со
 * стандартными бордюрами Word.
 */
public final class DocxTableWriter {

    private static final String FONT_FAMILY = "Times New Roman";
    private static final int FONT_SIZE = 12;

    private DocxTableWriter() {
    }

    public static void write(MarkdownTable table, OutputStream out) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable xTable = doc.createTable(
                    table.rows().size() + 1,
                    table.columnCount());

            applyDefaultLayout(xTable);

            // Заголовок
            XWPFTableRow headerRow = xTable.getRow(0);
            List<String> header = table.header();
            for (int c = 0; c < header.size(); c++) {
                fillCell(headerRow.getCell(c), header.get(c),
                        ParagraphAlignment.CENTER, true);
            }

            // Данные
            List<List<String>> rows = table.rows();
            for (int r = 0; r < rows.size(); r++) {
                XWPFTableRow row = xTable.getRow(r + 1);
                List<String> data = rows.get(r);
                for (int c = 0; c < data.size(); c++) {
                    String value = data.get(c);
                    ParagraphAlignment align = switch (CellClassifier.classify(value)) {
                        case NUMBER, DATE -> ParagraphAlignment.RIGHT;
                        case TEXT -> ParagraphAlignment.BOTH;
                    };
                    fillCell(row.getCell(c), value, align, false);
                }
            }

            doc.write(out);
        }
    }

    private static void applyDefaultLayout(XWPFTable table) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();

        // Ширина таблицы — 100% страницы.
        CTTblWidth width = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(BigInteger.valueOf(5000)); // 5000 = 100%

        // Стандартные тонкие чёрные бордюры.
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        setBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setBorder(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());
        setBorder(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());
    }

    private static void setBorder(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setSpace(BigInteger.valueOf(0));
        border.setColor("000000");
    }

    private static void fillCell(XWPFTableCell cell, String text,
                                 ParagraphAlignment alignment, boolean bold) {
        // В свежесозданной ячейке уже есть один пустой параграф — переиспользуем его.
        XWPFParagraph paragraph = cell.getParagraphs().isEmpty()
                ? cell.addParagraph()
                : cell.getParagraphs().get(0);

        paragraph.setAlignment(alignment);
        paragraph.setSpacingLineRule(LineSpacingRule.AUTO);
        paragraph.setSpacingBetween(1.5, LineSpacingRule.AUTO);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);

        // Убираем лишние раны, если параграф уже содержит что-то.
        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }

        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(FONT_SIZE);
        run.setBold(bold);
        run.setText(text == null ? "" : text);
    }
}
