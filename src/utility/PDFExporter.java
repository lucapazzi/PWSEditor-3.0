package utility;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * PDFExporter: exports Swing panels into a single-page PDF using Apache PDFBox.
 *
 * Note: add PDFBox jars (pdfbox + fontbox) to the classpath (e.g. put jars into
 * the project's `lib/` directory and include them when compiling/running).
 */
public class PDFExporter {
    /** Utility class; do not instantiate. */
    private PDFExporter() {
    }

    // If true, attempt vector rendering first. Default: false => raster-first high-quality.
    private static boolean preferVector = false;

    /**
     * Toggle preference for vector rendering. Default is false (raster-first).
     *
     * @param v true to prefer vector rendering
     */
    public static void setPreferVector(boolean v) { preferVector = v; }

    /**
     * Export a JPanel to a PDF file.
     * Renders the panel to a BufferedImage and embeds it in a single PDF page,
     * or uses a vector PDFGraphics2D renderer when preferred and available.
     *
     * @param panel panel to export
     * @param file output PDF file
     * @throws IOException if the file cannot be written
     */
    public static void exportPanelToPDF(JPanel panel, File file) throws IOException {
        if (panel == null) throw new IllegalArgumentException("panel is null");
        if (file == null) throw new IllegalArgumentException("file is null");

        int width = panel.getWidth();
        int height = panel.getHeight();
        if (width <= 0 || height <= 0) {
            java.awt.Dimension d = panel.getPreferredSize();
            width = Math.max(1, d.width);
            height = Math.max(1, d.height);
        }

        try (PDDocument doc = new PDDocument()) {
            PDRectangle rect = new PDRectangle(width, height);
            PDPage page = new PDPage(rect);
            doc.addPage(page);

            // Raster-first default (high-quality PNG at 4x)
            if (!preferVector) {
                int scale = 4;
                int imgW = Math.max(1, width * scale);
                int imgH = Math.max(1, height * scale);

                BufferedImage hiRes = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = hiRes.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.scale(scale, scale);
                panel.setDoubleBuffered(false);
                panel.printAll(g2);
                g2.dispose();
                panel.setDoubleBuffered(true);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(hiRes, "png", baos);
                baos.flush();
                byte[] pngBytes = baos.toByteArray();
                baos.close();

                PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, pngBytes, "panel.png");
                try (PDPageContentStream contents = new PDPageContentStream(doc, page)) {
                    contents.drawImage(pdImage, 0, 0, width, height);
                }
                doc.save(file);
                return;
            }

            // Vector-preferred path: attempt vector renderers, fallback to raster if unavailable
            String[] candidates = new String[] {
                    "org.apache.pdfbox.graphics2d.PDFGraphics2D",
                    "de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D",
                    "de.rototor.pdfbox.graphics2d.PDFGraphics2D",
                    "org.fit.pdfgraphics.PDFGraphics2D"
            };

            boolean vectorDone = false;
            for (String className : candidates) {
                try {
                    Class<?> cls = Class.forName(className);
                    java.lang.reflect.Constructor<?> ctor = null;
                    for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
                        Class<?>[] pts = c.getParameterTypes();
                        if (pts.length >= 2 && pts[0].getName().equals(PDDocument.class.getName()) && pts[1].getName().equals(PDPage.class.getName())) {
                            ctor = c;
                            break;
                        }
                    }
                    if (ctor != null) {
                        Object pdfG2 = null;
                        Class<?>[] pts = ctor.getParameterTypes();
                        if (pts.length == 2) {
                            pdfG2 = ctor.newInstance(doc, page);
                        } else if (pts.length == 3) {
                            pdfG2 = ctor.newInstance(doc, page, Boolean.FALSE);
                        } else {
                            pdfG2 = ctor.newInstance(doc, page);
                        }

                        if (pdfG2 instanceof java.awt.Graphics2D) {
                            Graphics2D g2 = (Graphics2D) pdfG2;
                            panel.printAll(g2);
                            try {
                                java.lang.reflect.Method dispose = pdfG2.getClass().getMethod("dispose");
                                dispose.invoke(pdfG2);
                            } catch (NoSuchMethodException ignored) {}
                            doc.save(file);
                            vectorDone = true;
                            break;
                        }
                    }
                } catch (ClassNotFoundException cnf) {
                    // Not present, try next candidate
                }
            }

            if (!vectorDone) {
                // Fallback raster (4x)
                int scale = 4;
                int imgW = Math.max(1, width * scale);
                int imgH = Math.max(1, height * scale);

                BufferedImage hiRes = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = hiRes.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.scale(scale, scale);
                panel.setDoubleBuffered(false);
                panel.printAll(g2);
                g2.dispose();
                panel.setDoubleBuffered(true);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(hiRes, "png", baos);
                baos.flush();
                byte[] pngBytes = baos.toByteArray();
                baos.close();

                PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, pngBytes, "panel.png");
                try (PDPageContentStream contents = new PDPageContentStream(doc, page)) {
                    contents.drawImage(pdImage, 0, 0, width, height);
                }
                doc.save(file);
            }

        } catch (NoClassDefFoundError ncd) {
            throw new UnsupportedOperationException("PDF export requires Apache PDFBox on the classpath (add pdfbox and fontbox jars).", ncd);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Failed to create PDF: " + ex.getMessage(), ex);
        }
    }
}
