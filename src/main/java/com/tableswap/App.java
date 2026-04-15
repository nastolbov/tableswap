package com.tableswap;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Swing-GUI для конвертера. Пользователь вставляет таблицу Markdown
 * или открывает .md-файл — приложение сохраняет .docx по ТЗ.
 */
public final class App extends JFrame {

    private final JTextArea inputArea = new JTextArea();
    private File lastDir;

    public App() {
        super("Markdown → DOCX: конвертер таблиц");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 640);
        setLocationRelativeTo(null);
        buildUi();
    }

    private void buildUi() {
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        inputArea.setLineWrap(false);
        inputArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        inputArea.setText("""
                | Дата       | Контрагент            | Сумма    |
                |------------|-----------------------|----------|
                | 2024-01-15 | ООО «Ромашка»         | 12 500,00|
                | 03.02.2024 | ИП Иванов             | 1 999,90 |
                | 17.02.2024 | Комиссия за перевод   | 150      |
                """);

        JScrollPane scroll = new JScrollPane(inputArea);
        scroll.setPreferredSize(new Dimension(880, 520));

        JButton openBtn = new JButton("Открыть .md…");
        openBtn.addActionListener(e -> openMarkdown());

        JButton pasteBtn = new JButton("Вставить из буфера");
        pasteBtn.addActionListener(e -> inputArea.paste());

        JButton clearBtn = new JButton("Очистить");
        clearBtn.addActionListener(e -> inputArea.setText(""));

        JButton convertBtn = new JButton("Сохранить как .docx…");
        convertBtn.addActionListener(e -> convert());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(openBtn);
        buttons.add(pasteBtn);
        buttons.add(clearBtn);
        buttons.add(convertBtn);

        setLayout(new BorderLayout());
        add(buttons, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    private void openMarkdown() {
        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setDialogTitle("Выберите Markdown-файл");
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown (*.md, *.markdown, *.txt)",
                "md", "markdown", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        lastDir = file.getParentFile();
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            inputArea.setText(text);
            inputArea.setCaretPosition(0);
        } catch (IOException ex) {
            showError("Не удалось прочитать файл:\n" + ex.getMessage());
        }
    }

    private void convert() {
        String source = inputArea.getText();
        MarkdownTable table;
        try {
            table = MarkdownTableParser.parse(source);
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
            return;
        }

        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setDialogTitle("Сохранить .docx");
        chooser.setSelectedFile(new File("table.docx"));
        chooser.setFileFilter(new FileNameExtensionFilter("Документ Word (*.docx)", "docx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        lastDir = target.getParentFile();
        if (!target.getName().toLowerCase().endsWith(".docx")) {
            target = new File(target.getParentFile(), target.getName() + ".docx");
        }

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            DocxTableWriter.write(table, out);
        } catch (IOException ex) {
            showError("Не удалось записать .docx:\n" + ex.getMessage());
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Готово!\nСохранено: " + target.getAbsolutePath()
                        + "\nКолонок: " + table.columnCount()
                        + ", строк данных: " + table.rows().size(),
                "Успех", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--cli".equals(args[0])) {
            // Headless-режим: java -jar md-to-docx.jar --cli in.md out.docx
            Path in = Path.of(args[1]);
            Path out = args.length >= 3 ? Path.of(args[2])
                    : in.resolveSibling(in.getFileName().toString().replaceAll("\\.[^.]+$", "") + ".docx");
            MarkdownTable table = MarkdownTableParser.parse(Files.readString(in, StandardCharsets.UTF_8));
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out.toFile()))) {
                DocxTableWriter.write(table, os);
            }
            System.out.println("Сохранено: " + out.toAbsolutePath());
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // используем кросс-платформенный L&F
        }
        SwingUtilities.invokeLater(() -> new App().setVisible(true));
    }
}
