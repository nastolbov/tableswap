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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
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

    /**
     * Полезная ширина страницы в твипах (A4, поля 2.5 см): 21 см − 2 × 2.5 см ≈ 16 см.
     * 1 см ≈ 567 твипов, 16 см ≈ 9072 твипа. Берём с запасом для равномерного деления.
     */
    private static final int USABLE_PAGE_WIDTH_TWIPS = 9000;

    private DocxTableWriter() {
    }

    public static void write(MarkdownTable table, OutputStream out) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            if (table.caption() != null && !table.caption().isBlank()) {
                writeCaption(doc, table.caption());
            }

            XWPFTable xTable = doc.createTable(
                    table.rows().size() + 1,
                    table.columnCount());

            int cols = table.columnCount();
            int colWidth = USABLE_PAGE_WIDTH_TWIPS / Math.max(cols, 1);
            applyDefaultLayout(xTable, cols, colWidth);

            // Заголовок
            XWPFTableRow headerRow = xTable.getRow(0);
            List<String> header = table.header();
            for (int c = 0; c < header.size(); c++) {
                XWPFTableCell cell = headerRow.getCell(c);
                setCellWidth(cell, colWidth);
                fillCell(cell, header.get(c), ParagraphAlignment.CENTER, true);
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
                    XWPFTableCell cell = row.getCell(c);
                    setCellWidth(cell, colWidth);
                    fillCell(cell, value, align, false);
                }
            }

            doc.write(out);
        }
    }

    private static void writeCaption(XWPFDocument doc, String caption) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingLineRule(LineSpacingRule.AUTO);
        paragraph.setSpacingBetween(1.5, LineSpacingRule.AUTO);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);

        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(FONT_SIZE);
        run.setBold(true);
        run.setText(caption);
    }

    private static void applyDefaultLayout(XWPFTable table, int columnCount, int colWidth) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();

        // Фиксируем ширину таблицы в твипах (100% полезной ширины A4 с полями 2.5 см).
        // С явной шириной Word не «съедет» влево за поле и не начнёт перекрывать колонки.
        CTTblWidth width = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf((long) colWidth * columnCount));

        // Нулевой отступ слева: таблица встаёт впритык к левому полю страницы.
        CTTblWidth indent = tblPr.isSetTblInd() ? tblPr.getTblInd() : tblPr.addNewTblInd();
        indent.setType(STTblWidth.DXA);
        indent.setW(BigInteger.ZERO);

        // Автоподгонка: Word сам будет сжимать/тянуть колонки под содержимое,
        // чтобы длинный текст не «смешивался» в соседние ячейки.
        CTTblLayoutType layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.AUTOFIT);

        // Сетка колонок (tblGrid) обязательна для корректного рендеринга в Word:
        // без неё Word угадывает ширины и часто промахивается.
        // tblGrid в CTTbl — обязательный элемент, поэтому всегда существует;
        // на всякий случай проверяем на null и создаём, если POI его не положил.
        CTTblGrid grid = ctTbl.getTblGrid();
        if (grid == null) {
            grid = ctTbl.addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int i = 0; i < columnCount; i++) {
            grid.addNewGridCol().setW(BigInteger.valueOf(colWidth));
        }

        // Стандартные тонкие чёрные бордюры.
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        setBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setBorder(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());
        setBorder(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());
    }

    private static void setCellWidth(XWPFTableCell cell, int widthTwips) {
        CTTcPr tcPr = cell.getCTTc().getTcPr() != null
                ? cell.getCTTc().getTcPr()
                : cell.getCTTc().addNewTcPr();
        CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        tcW.setType(STTblWidth.DXA);
        tcW.setW(BigInteger.valueOf(widthTwips));
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
