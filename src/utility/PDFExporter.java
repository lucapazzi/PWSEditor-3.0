package utility;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;

/**
 * PDFExporter: exports Swing panels into a single-page vector PDF using PDFBox + pdfbox-graphics2d.
 *
 * Note: add PDFBox (pdfbox + fontbox) and pdfbox-graphics2d jars to the classpath.
 */
public class PDFExporter {
    /** Utility class; do not instantiate. */
    private PDFExporter() {
    }

    /**
     * Export a JPanel to a vector PDF file.
     *
     * @param panel panel to export
     * @param file output PDF file
     * @throws IOException if the file cannot be written
     */
    public static void exportPanelToPDF(JPanel panel, File file) throws IOException {
        exportPanelToPDF(panel, file, null);
    }

    /**
     * Export a region of a JPanel to a vector PDF file.
     *
     * @param panel panel to export
     * @param file output PDF file
     * @param region panel-space region to export; null exports the full panel
     * @throws IOException if the file cannot be written
     */
    public static void exportPanelToPDF(JPanel panel, File file, Rectangle region) throws IOException {
        if (panel == null) throw new IllegalArgumentException("panel is null");
        if (file == null) throw new IllegalArgumentException("file is null");

        Rectangle exportRect = resolveExportRect(panel, region);
        int width = exportRect.width;
        int height = exportRect.height;

        try (PDDocument doc = new PDDocument()) {
            PDRectangle rect = new PDRectangle(width, height);
            PDPage page = new PDPage(rect);
            doc.addPage(page);

            PdfBoxGraphics2D g2 = new PdfBoxGraphics2D(doc, width, height);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            boolean oldDoubleBuffered = panel.isDoubleBuffered();
            panel.setDoubleBuffered(false);
            try {
                g2.translate(-exportRect.x, -exportRect.y);
                panel.printAll(g2);
            } finally {
                g2.dispose();
                panel.setDoubleBuffered(oldDoubleBuffered);
            }

            PDFormXObject xform = g2.getXFormObject();
            try (PDPageContentStream contents = new PDPageContentStream(doc, page)) {
                contents.drawForm(xform);
            }
            doc.save(file);

        } catch (NoClassDefFoundError ncd) {
            throw new UnsupportedOperationException("PDF export requires PDFBox and pdfbox-graphics2d on the classpath.", ncd);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Failed to create PDF: " + ex.getMessage(), ex);
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
