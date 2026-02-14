package utility;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Utility to place PDF data in the system clipboard. */
public final class PDFClipboard {
    private static final DataFlavor PDF_STREAM_FLAVOR;

    static {
        try {
            PDF_STREAM_FLAVOR = new DataFlavor("application/pdf;class=java.io.InputStream");
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private PDFClipboard() {
    }

    public static void putPDFOnSystemClipboard(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("pdfBytes is empty");
        }

        File tempPdf = File.createTempFile("pwsexport-", ".pdf");
        tempPdf.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
            fos.write(pdfBytes);
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            clipboard.setContents(new PDFTransferable(pdfBytes, tempPdf), null);
        } catch (IllegalStateException ex) {
            throw new IOException("Clipboard is currently unavailable. Please try again.", ex);
        }
    }

    private static final class PDFTransferable implements Transferable {
        private static final DataFlavor[] FLAVORS = new DataFlavor[] {
            PDF_STREAM_FLAVOR,
            DataFlavor.javaFileListFlavor
        };

        private final byte[] pdfBytes;
        private final List<File> files;

        private PDFTransferable(byte[] pdfBytes, File tempPdf) {
            this.pdfBytes = pdfBytes.clone();
            this.files = Collections.singletonList(tempPdf);
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return FLAVORS.clone();
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            for (DataFlavor f : FLAVORS) {
                if (f.equals(flavor)) return true;
            }
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (PDF_STREAM_FLAVOR.equals(flavor)) {
                return new ByteArrayInputStream(pdfBytes);
            }
            if (DataFlavor.javaFileListFlavor.equals(flavor)) {
                return files;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
