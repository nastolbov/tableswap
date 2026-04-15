package com.tableswap;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/**
 * {@link Transferable}, который кладёт в системный буфер обмена HTML
 * и plain-text одновременно. Word (macOS, Windows) при {@code Cmd/Ctrl+V}
 * сам подхватывает HTML и превращает его в нативную Word-таблицу.
 */
public final class HtmlSelection implements Transferable {

    private static final DataFlavor HTML_UTF8;
    private static final DataFlavor HTML_DEFAULT;

    static {
        try {
            HTML_UTF8 = new DataFlavor("text/html;charset=UTF-8;class=java.lang.String");
            HTML_DEFAULT = new DataFlavor("text/html;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final String html;
    private final String plain;
    private final DataFlavor[] flavors;

    public HtmlSelection(String html, String plain) {
        this.html = html;
        this.plain = plain == null ? "" : plain;
        this.flavors = new DataFlavor[] { HTML_UTF8, HTML_DEFAULT, DataFlavor.stringFlavor };
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return flavors.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor f : flavors) {
            if (f.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (HTML_UTF8.equals(flavor) || HTML_DEFAULT.equals(flavor)) {
            return html;
        }
        if (DataFlavor.stringFlavor.equals(flavor)) {
            return plain;
        }
        throw new UnsupportedFlavorException(flavor);
    }
}
