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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblCellMar;
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
            int[] colWidths = computeColumnWidths(table, USABLE_PAGE_WIDTH_TWIPS);
            applyDefaultLayout(xTable, colWidths);

            // Заголовок
            XWPFTableRow headerRow = xTable.getRow(0);
            List<String> header = table.header();
            for (int c = 0; c < header.size(); c++) {
                XWPFTableCell cell = headerRow.getCell(c);
                setCellWidth(cell, colWidths[c]);
                fillCell(cell, header.get(c), ParagraphAlignment.CENTER, true);
            }

            // Данные
            List<List<String>> rows = table.rows();
            for (int r = 0; r < rows.size(); r++) {
                XWPFTableRow row = xTable.getRow(r + 1);
                List<String> data = rows.get(r);
                for (int c = 0; c < cols; c++) {
                    String value = c < data.size() ? data.get(c) : "";
                    ParagraphAlignment align = switch (CellClassifier.classify(value)) {
                        case NUMBER, DATE -> ParagraphAlignment.RIGHT;
                        case TEXT -> ParagraphAlignment.BOTH;
                    };
                    XWPFTableCell cell = row.getCell(c);
                    setCellWidth(cell, colWidths[c]);
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

    private static void applyDefaultLayout(XWPFTable table, int[] colWidths) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();

        long total = 0;
        for (int w : colWidths) {
            total += w;
        }

        // Фиксируем ширину таблицы в твипах (полезная ширина A4 с полями 2.5 см).
        // С явной шириной Word не «съедет» влево за поле и не начнёт перекрывать колонки.
        CTTblWidth width = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(total));

        // Нулевой отступ слева: таблица встаёт впритык к левому полю страницы.
        CTTblWidth indent = tblPr.isSetTblInd() ? tblPr.getTblInd() : tblPr.addNewTblInd();
        indent.setType(STTblWidth.DXA);
        indent.setW(BigInteger.ZERO);

        // Автоподгонка: Word сам будет сжимать/тянуть колонки под содержимое,
        // чтобы длинный текст не «смешивался» в соседние ячейки.
        CTTblLayoutType layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.AUTOFIT);

        // Внутренние отступы ячеек: слева/справа 108 твипов (~0.19 см, стандарт Word),
        // сверху/снизу 40 твипов. Без них текст «слипается» с границами и соседями.
        CTTblCellMar mar = tblPr.isSetTblCellMar() ? tblPr.getTblCellMar() : tblPr.addNewTblCellMar();
        setCellMargin(mar.isSetTop() ? mar.getTop() : mar.addNewTop(), 40);
        setCellMargin(mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom(), 40);
        setCellMargin(mar.isSetLeft() ? mar.getLeft() : mar.addNewLeft(), 108);
        setCellMargin(mar.isSetRight() ? mar.getRight() : mar.addNewRight(), 108);

        // Сетка колонок (tblGrid) обязательна для корректного рендеринга в Word:
        // без неё Word угадывает ширины и часто промахивается.
        CTTblGrid grid = ctTbl.getTblGrid();
        if (grid == null) {
            grid = ctTbl.addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int w : colWidths) {
            grid.addNewGridCol().setW(BigInteger.valueOf(w));
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

    private static void setCellMargin(CTTblWidth margin, int twips) {
        margin.setType(STTblWidth.DXA);
        margin.setW(BigInteger.valueOf(twips));
    }

    /**
     * Подбирает ширины колонок пропорционально самой длинной строке в каждой
     * колонке, с мягкими ограничениями — чтобы узкая колонка (например, «№»)
     * не расползалась, а очень длинная колонка не съедала всё место.
     */
    private static int[] computeColumnWidths(MarkdownTable table, int totalTwips) {
        int cols = table.columnCount();
        int[] maxLen = new int[cols];
        for (int c = 0; c < cols; c++) {
            maxLen[c] = longestLine(table.header().get(c));
        }
        for (List<String> row : table.rows()) {
            for (int c = 0; c < cols && c < row.size(); c++) {
                maxLen[c] = Math.max(maxLen[c], longestLine(row.get(c)));
            }
        }

        // Вес ∈ [3, 30] — ограничивает и слишком узкие, и слишком широкие столбцы.
        double[] weight = new double[cols];
        double sum = 0;
        for (int c = 0; c < cols; c++) {
            weight[c] = Math.max(3, Math.min(maxLen[c], 30));
            sum += weight[c];
        }

        int[] widths = new int[cols];
        int assigned = 0;
        for (int c = 0; c < cols - 1; c++) {
            widths[c] = (int) Math.round(weight[c] * totalTwips / sum);
            assigned += widths[c];
        }
        widths[cols - 1] = totalTwips - assigned;

        // Жёсткий минимум, чтобы никакая колонка не схлопнулась.
        final int minWidth = 600;
        for (int c = 0; c < cols; c++) {
            if (widths[c] < minWidth) {
                int deficit = minWidth - widths[c];
                widths[c] = minWidth;
                int largest = 0;
                for (int j = 1; j < cols; j++) {
                    if (widths[j] > widths[largest]) {
                        largest = j;
                    }
                }
                widths[largest] -= deficit;
            }
        }
        return widths;
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
