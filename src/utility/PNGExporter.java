package utility;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * PNGExporter: exports Swing panels into PNG images.
 */
public class PNGExporter {
    /** Utility class; do not instantiate. */
    private PNGExporter() {
    }

    /**
     * Export a JPanel to a PNG file.
     *
     * @param panel panel to export
     * @param file output PNG file
     * @throws IOException if the file cannot be written
     */
    public static void exportPanelToPNG(JPanel panel, File file) throws IOException {
        if (panel == null) throw new IllegalArgumentException("panel is null");
        if (file == null) throw new IllegalArgumentException("file is null");

        int width = panel.getWidth();
        int height = panel.getHeight();
        if (width <= 0 || height <= 0) {
            java.awt.Dimension d = panel.getPreferredSize();
            width = Math.max(1, d.width);
            height = Math.max(1, d.height);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        boolean oldDoubleBuffered = panel.isDoubleBuffered();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            panel.setDoubleBuffered(false);
            panel.printAll(g2);
        } finally {
            panel.setDoubleBuffered(oldDoubleBuffered);
            g2.dispose();
        }

        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG writer is available.");
        }
    }
}
