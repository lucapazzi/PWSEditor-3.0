package utility;

import javax.imageio.ImageIO;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Utility to place PNG data in the system clipboard. */
public final class PNGClipboard {
    private static final DataFlavor PNG_STREAM_FLAVOR;

    static {
        try {
            PNG_STREAM_FLAVOR = new DataFlavor("image/png;class=java.io.InputStream");
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private PNGClipboard() {
    }

    public static void putPNGOnSystemClipboard(BufferedImage image) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("image is null");
        }

        byte[] pngBytes = toPngBytes(image);
        File tempPng = File.createTempFile("pwsexport-", ".png");
        tempPng.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempPng)) {
            fos.write(pngBytes);
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            clipboard.setContents(new PNGTransferable(image, pngBytes, tempPng), null);
        } catch (IllegalStateException ex) {
            throw new IOException("Clipboard is currently unavailable. Please try again.", ex);
        }
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", baos)) {
                throw new IOException("No PNG writer is available.");
            }
            return baos.toByteArray();
        }
    }

    private static final class PNGTransferable implements Transferable {
        private static final DataFlavor[] FLAVORS = new DataFlavor[] {
            DataFlavor.imageFlavor,
            PNG_STREAM_FLAVOR,
            DataFlavor.javaFileListFlavor
        };

        private final BufferedImage image;
        private final byte[] pngBytes;
        private final List<File> files;

        private PNGTransferable(BufferedImage image, byte[] pngBytes, File tempPng) {
            this.image = image;
            this.pngBytes = pngBytes.clone();
            this.files = Collections.singletonList(tempPng);
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
            if (DataFlavor.imageFlavor.equals(flavor)) {
                return image;
            }
            if (PNG_STREAM_FLAVOR.equals(flavor)) {
                return new ByteArrayInputStream(pngBytes);
            }
            if (DataFlavor.javaFileListFlavor.equals(flavor)) {
                return files;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
