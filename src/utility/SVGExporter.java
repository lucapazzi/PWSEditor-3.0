package utility;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.Base64;

/** Exports Swing components to SVG using an embedded raster image. */
public class SVGExporter {

    public static void exportPanelToSVG(JPanel panel) {
        // Fallback: render panel to PNG and embed as base64 inside a minimal SVG
        int w = panel.getWidth() > 0 ? panel.getWidth() : panel.getPreferredSize().width;
        int h = panel.getHeight() > 0 ? panel.getHeight() : panel.getPreferredSize().height;
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        panel.printAll(g2);
        g2.dispose();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + w + "\" height=\"" + h + "\">\n" +
                    "<image href=\"data:image/png;base64," + b64 + "\" width=\"" + w + "\" height=\"" + h + "\" />\n" +
                    "</svg>";
            StringSelection selection = new StringSelection(svg);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(panel, "SVG (as embedded PNG) exported to clipboard.");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void exportPanelToSVGFile(JPanel panel, File file) {
        // Fallback: render panel to PNG and write a minimal SVG embedding the PNG as base64
        int w = panel.getWidth() > 0 ? panel.getWidth() : panel.getPreferredSize().width;
        int h = panel.getHeight() > 0 ? panel.getHeight() : panel.getPreferredSize().height;
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        panel.printAll(g2);
        g2.dispose();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + w + "\" height=\"" + h + "\">\n" +
                    "<image href=\"data:image/png;base64," + b64 + "\" width=\"" + w + "\" height=\"" + h + "\" />\n" +
                    "</svg>";
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(svg);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(panel, "Error saving SVG file: " + ex.getMessage());
        }
    }
}
