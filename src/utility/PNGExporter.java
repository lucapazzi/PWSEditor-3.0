package utility;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * PNGExporter: exports Swing panels into PNG images.
 */
public class PNGExporter {
    private static final int PNG_EXPORT_SCALE = 2;

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
        exportPanelToPNG(panel, file, null);
    }

    /**
     * Export a region of a JPanel to a PNG file.
     *
     * @param panel panel to export
     * @param file output PNG file
     * @param region panel-space region to export; null exports the full panel
     * @throws IOException if the file cannot be written
     */
    public static void exportPanelToPNG(JPanel panel, File file, Rectangle region) throws IOException {
        if (panel == null) throw new IllegalArgumentException("panel is null");
        if (file == null) throw new IllegalArgumentException("file is null");
        BufferedImage image = renderPanelToImage(panel, region);
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG writer is available.");
        }
    }

    /**
     * Export a region of a JPanel and place the resulting PNG in the system clipboard.
     *
     * @param panel panel to export
     * @param region panel-space region to export; null exports the full panel
     * @throws IOException if the PNG cannot be generated or clipboard update fails
     */
    public static void exportPanelToClipboard(JPanel panel, Rectangle region) throws IOException {
        if (panel == null) throw new IllegalArgumentException("panel is null");
        BufferedImage image = renderPanelToImage(panel, region);
        PNGClipboard.putPNGOnSystemClipboard(image);
    }

    private static BufferedImage renderPanelToImage(JPanel panel, Rectangle region) {
        Rectangle exportRect = resolveExportRect(panel, region);
        int width = exportRect.width;
        int height = exportRect.height;
        int scaledWidth = Math.max(1, width * PNG_EXPORT_SCALE);
        int scaledHeight = Math.max(1, height * PNG_EXPORT_SCALE);

        BufferedImage image = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        boolean oldDoubleBuffered = panel.isDoubleBuffered();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, scaledWidth, scaledHeight);
            panel.setDoubleBuffered(false);
            g2.scale(PNG_EXPORT_SCALE, PNG_EXPORT_SCALE);
            g2.translate(-exportRect.x, -exportRect.y);
            panel.printAll(g2);
            return image;
        } finally {
            panel.setDoubleBuffered(oldDoubleBuffered);
            g2.dispose();
        }
    }

    private static Rectangle resolveExportRect(JPanel panel, Rectangle region) {
        int width = panel.getWidth();
        int height = panel.getHeight();
        if (width <= 0 || height <= 0) {
            java.awt.Dimension d = panel.getPreferredSize();
            width = Math.max(1, d.width);
            height = Math.max(1, d.height);
        }
        Rectangle panelRect = new Rectangle(0, 0, width, height);
        if (region == null) {
            return panelRect;
        }
        Rectangle clipped = panelRect.intersection(region);
        if (clipped.width <= 0 || clipped.height <= 0) {
            return panelRect;
        }
        return clipped;
    }
}
